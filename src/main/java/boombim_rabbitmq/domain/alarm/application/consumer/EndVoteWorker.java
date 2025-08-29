package boombim_rabbitmq.domain.alarm.application.consumer;

import boombim_rabbitmq.domain.alarm.application.messaging.EndVoteMessage;
import boombim_rabbitmq.domain.alarm.application.service.FcmSender;
import boombim_rabbitmq.domain.alarm.domain.entity.alarm.Alarm;
import boombim_rabbitmq.domain.alarm.domain.entity.alarm.AlarmRecipient;
import boombim_rabbitmq.domain.alarm.domain.entity.alarm.fcm.FcmToken;
import boombim_rabbitmq.domain.alarm.domain.entity.alarm.type.AlarmStatus;
import boombim_rabbitmq.domain.alarm.domain.entity.alarm.type.AlarmType;
import boombim_rabbitmq.domain.alarm.domain.entity.member.Member;
import boombim_rabbitmq.domain.alarm.domain.entity.vote.Vote;
import boombim_rabbitmq.domain.alarm.domain.repository.alarm.AlarmRecipientRepository;
import boombim_rabbitmq.domain.alarm.domain.repository.alarm.AlarmRepository;
import boombim_rabbitmq.domain.alarm.domain.repository.alarm.fcm.FcmTokenRepository;
import boombim_rabbitmq.domain.alarm.domain.repository.member.MemberRepository;
import boombim_rabbitmq.domain.alarm.domain.repository.vote.VoteAnswerRepository;
import boombim_rabbitmq.domain.alarm.domain.repository.vote.VoteDuplicationRepository;
import boombim_rabbitmq.domain.alarm.domain.repository.vote.VoteRepository;
import boombim_rabbitmq.domain.alarm.presentation.dto.AlarmSendResult;
import boombim_rabbitmq.global.config.RabbitMQConfig;
import boombim_rabbitmq.global.infra.exception.error.BoombimException;
import boombim_rabbitmq.global.infra.exception.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EndVoteWorker {

    private final VoteRepository voteRepository;
    private final MemberRepository userRepository;
    private final AlarmRepository alarmRepository;
    private final AlarmRecipientRepository alarmRecipientRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final FcmSender fcmSender;
    private final VoteDuplicationRepository voteDuplicationRepository;
    private final VoteAnswerRepository voteAnswerRepository;

    private static final int BATCH_SIZE = 500;

    @Value("${admin.id}")
    private String adminId;

    @RabbitListener(queues = RabbitMQConfig.Q_END_VOTE, concurrency = "3-5")
    public void onEndVote(EndVoteMessage msg) {
        log.info("투표 종료 알림 처리 시작: voteId={}, isQuestioner={}", msg.getVoteId(), msg.isQuestioner());

        Vote vote = voteRepository.findById(msg.getVoteId())
                .orElseThrow(() -> new BoombimException(ErrorCode.VOTE_NOT_EXIST));

        Member sender = userRepository.findById(adminId)
                .orElseThrow(() -> new BoombimException(ErrorCode.USER_NOT_EXIST));

        // (1) 알림 메시지 생성 및 저장
        AlarmMessage alarmMessage = createAlarmMessage(vote, msg, sender);

        // (2) 알림 대상자 조회
        List<Member> targets = msg.isQuestioner()
                ? getBaseMembers(vote)
                : getAnswerersOnly(vote);
        if (targets.isEmpty()) return;

        // (3) 푸시 전송
        sendFcm(vote, targets, alarmMessage);
    }

    // 알람 메시지 생성
    private AlarmMessage createAlarmMessage(Vote vote, EndVoteMessage msg, Member sender) {
        String memberName = vote.getMember().getName();

        String title = msg.isQuestioner()
                ? "[투표 종료] 질문자 알림"
                : "[투표 종료] 투표자 알림";

        String body = msg.isQuestioner()
                ? memberName + "님! 만든 투표가 종료되었습니다."
                : "참여한 투표가 종료되었습니다.";

        Alarm alarm = alarmRepository.save(Alarm.builder()
                .title(title)
                .message(body)
                .type(AlarmType.VOTE)
                .sender(sender)
                .build());

        alarm.updateStatus(AlarmStatus.SENDING);
        return new AlarmMessage(alarm, title, body);
    }

    // 푸시 전송
    private void sendFcm(Vote vote, List<Member> targets, AlarmMessage alarmMessage) {
        List<FcmToken> tokens = fcmTokenRepository.findActiveTokensForUsers(targets);
        if (tokens.isEmpty()) return;

        log.info("투표 종료 알림 전송 시작: voteId={}, 총 대상자={}, 유효 토큰={}",
                vote.getId(), targets.size(), tokens.size());

        int success = 0, failure = 0;
        List<String> invalidTokens = new ArrayList<>();
        List<AlarmRecipient> recipients = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i += BATCH_SIZE) {
            List<FcmToken> batch = tokens.subList(i, Math.min(i + BATCH_SIZE, tokens.size()));
            log.info("=== FCM 배치 전송 시작 (voteId={}, batchStart={}, batchEnd={}) ===",
                    vote.getId(), i, i + batch.size());

            AlarmSendResult result = fcmSender.sendBatchNotification(batch, alarmMessage.title, alarmMessage.body);

            log.info("배치 전송 결과 (voteId={}): 성공={}, 실패={}, 무효토큰={}",
                    vote.getId(), result.successCount(), result.failureCount(), result.invalidTokens().size());

            success += result.successCount();
            failure += result.failureCount();
            invalidTokens.addAll(result.invalidTokens());

            recipients.addAll(buildRecipients(alarmMessage.alarm, batch, result.invalidTokens()));
        }

        alarmRecipientRepository.saveAll(recipients);
        deactivateInvalidTokens(invalidTokens);

        if (success > 0) {
            alarmMessage.alarm.updateStatus(AlarmStatus.SENT);
        } else {
            alarmMessage.alarm.updateFailureReason("모든 대상자에게 전송 실패");
        }

        log.info("투표 종료 알림 처리 완료: alarmId={}, voteId={}, 성공={}, 실패={}, 무효토큰={}",
                alarmMessage.alarm.getId(), vote.getId(), success, failure, invalidTokens.size());
    }

    private List<AlarmRecipient> buildRecipients(Alarm alarm, List<FcmToken> batch, List<String> invalidTokens) {
        Set<String> invalidSet = new HashSet<>(invalidTokens);
        List<AlarmRecipient> recipients = new ArrayList<>();
        for (FcmToken ft : batch) {
            AlarmRecipient ar = AlarmRecipient.builder()
                    .alarm(alarm)
                    .member(ft.getMember())
                    .deviceType(ft.getDeviceType())
                    .build();
            if (invalidSet.contains(ft.getToken())) {
                ar.markFailed("INVALID_OR_UNREGISTERED");
            } else {
                ar.markSent();
            }
            recipients.add(ar);
        }
        return recipients;
    }

    private void deactivateInvalidTokens(List<String> invalidTokens) {
        for (String token : invalidTokens) {
            fcmTokenRepository.findByToken(token).ifPresent(FcmToken::deactivate);
        }
        log.info("무효 토큰 {}개 비활성화 완료", invalidTokens.size());
    }

    // 타겟 조회
    private List<Member> getBaseMembers(Vote vote) {
        List<Member> creators = voteRepository.findMembersByVote(vote);
        List<Member> duplicators = voteDuplicationRepository.findMembersByVote(vote);

        Map<String, Member> byId = new LinkedHashMap<>();
        creators.forEach(m -> byId.put(m.getId(), m));
        duplicators.forEach(m -> byId.put(m.getId(), m));

        return new ArrayList<>(byId.values());
    }

    private List<Member> getAnswerersOnly(Vote vote) {
        Set<String> baseIds = getBaseMembers(vote).stream()
                .map(Member::getId)
                .collect(Collectors.toSet());

        return voteAnswerRepository.findMembersByVote(vote).stream()
                .filter(m -> !baseIds.contains(m.getId()))
                .distinct()
                .collect(Collectors.toList());
    }

    // 내부 DTO
    private record AlarmMessage(Alarm alarm, String title, String body) {}
}
