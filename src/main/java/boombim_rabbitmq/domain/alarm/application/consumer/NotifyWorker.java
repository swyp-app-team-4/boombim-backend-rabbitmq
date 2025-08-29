package boombim_rabbitmq.domain.alarm.application.consumer;

import boombim_rabbitmq.domain.alarm.application.messaging.NotifyMessage;
import boombim_rabbitmq.domain.alarm.application.service.FcmSender;
import boombim_rabbitmq.domain.alarm.domain.entity.alarm.Alarm;
import boombim_rabbitmq.domain.alarm.domain.entity.alarm.AlarmRecipient;
import boombim_rabbitmq.domain.alarm.domain.entity.alarm.fcm.FcmToken;
import boombim_rabbitmq.domain.alarm.domain.entity.alarm.type.AlarmStatus;
import boombim_rabbitmq.domain.alarm.domain.repository.alarm.AlarmRecipientRepository;
import boombim_rabbitmq.domain.alarm.domain.repository.alarm.AlarmRepository;
import boombim_rabbitmq.domain.alarm.domain.repository.alarm.fcm.FcmTokenRepository;
import boombim_rabbitmq.domain.alarm.infra.messaging.PushProducer;
import boombim_rabbitmq.domain.alarm.presentation.dto.AlarmSendResult;
import boombim_rabbitmq.global.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotifyWorker {

    private final AlarmRepository alarmRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final AlarmRecipientRepository alarmRecipientRepository;
    private final FcmSender fcmSender;
    private final PushProducer pushProducer;

    private static final int MAX_RETRY = 4; // 0~4 → 총 5회
    private static final int BATCH_SIZE = 500;

    @RabbitListener(queues = RabbitMQConfig.Q_NOTIFY_NOW)
    public void onNotify(NotifyMessage msg) {
        Long alarmId = msg.getAlarmId();
        Alarm alarm = findAlarmWithRetry(alarmId);

        try {
            alarm.updateStatus(AlarmStatus.SENDING);

            // (1) 대상 토큰 조회
            List<FcmToken> allTokens = fcmTokenRepository.findAllActiveTokens();

            // (2) 배치 전송
            AlarmSendResultSummary summary = sendNotifications(alarm, msg, allTokens);

            // (3) 무효 토큰 처리 & 수신자 저장
            deactivateInvalidTokens(summary.invalidTokens);
            alarmRecipientRepository.saveAll(summary.recipients);

            // (4) 알람 상태 업데이트
            updateAlarmStatus(alarm, summary);

            log.info("푸시 전송 완료: alarmId={}, success={}, failure={}",
                    alarmId, summary.totalSuccess, summary.totalFailure);

        } catch (Exception e) {
            handleFailure(alarm, msg, e);
        }
    }

    // Alarm 조회 시 DB 반영 지연을 고려해서 최대 3번까지 재시도 (200ms 간격)
    private Alarm findAlarmWithRetry(Long alarmId) {
        Alarm alarm = null;
        for (int i = 0; i < 3; i++) {
            alarm = alarmRepository.findById(alarmId).orElse(null);
            if (alarm != null) break;
            try {
                Thread.sleep(200); // DB commit 대기
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        if (alarm == null) {
            throw new IllegalStateException("Alarm not found: " + alarmId);
        }
        return alarm;
    }

    // FCM 토큰들을 배치 단위로 묶어서 알림 전송 → 결과 요약 (성공/실패/무효토큰/수신자 리스트)
    private AlarmSendResultSummary sendNotifications(Alarm alarm, NotifyMessage msg, List<FcmToken> tokens) {
        int totalSuccess = 0, totalFailure = 0;
        List<String> invalidTokens = new ArrayList<>();
        List<AlarmRecipient> recipients = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i += BATCH_SIZE) {
            List<FcmToken> batch = tokens.subList(i, Math.min(i + BATCH_SIZE, tokens.size()));
            AlarmSendResult result = fcmSender.sendBatchNotification(batch, msg.getTitle(), msg.getBody());

            totalSuccess += result.successCount();
            totalFailure += result.failureCount();
            invalidTokens.addAll(result.invalidTokens());

            recipients.addAll(buildRecipients(alarm, batch, result.invalidTokens()));
        }

        return new AlarmSendResultSummary(totalSuccess, totalFailure, invalidTokens, recipients);
    }

    // 각 FCM 토큰을 기반으로 AlarmRecipient 엔티티 생성 (성공/실패 여부 기록)
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

    // 무효 토큰들을 찾아서 비활성화 처리
    private void deactivateInvalidTokens(List<String> invalidTokens) {
        for (String token : invalidTokens) {
            fcmTokenRepository.findByToken(token).ifPresent(FcmToken::deactivate);
        }
        log.info("무효한 토큰 {} 개 비활성화 완료", invalidTokens.size());
    }

    // Alarm 전송 결과에 따라 상태를 업데이트 (성공/실패 여부 기록)
    private void updateAlarmStatus(Alarm alarm, AlarmSendResultSummary summary) {
        if (summary.totalSuccess > 0 && summary.totalFailure == 0) {
            alarm.updateStatus(AlarmStatus.SENT);
        } else if (summary.totalSuccess > 0) {
            alarm.updateStatus(AlarmStatus.SENT);
        } else {
            alarm.updateFailureReason("모든 대상자에게 전송 실패");
        }
    }

    // 전송 실패 시 로그 기록 + 재시도 큐 발행 (MAX_RETRY 초과 시 실패 처리)
    private void handleFailure(Alarm alarm, NotifyMessage msg, Exception e) {
        log.error("푸시 전송 처리 실패: alarmId={}, retryCount={}, err={}",
                msg.getAlarmId(), msg.getRetryCount(), e.getMessage(), e);

        int nextRetry = (msg.getRetryCount() == null ? 0 : msg.getRetryCount()) + 1;
        if (nextRetry <= MAX_RETRY) {
            long delay = computeBackoffMillis(nextRetry);
            pushProducer.publishRetry(NotifyMessage.builder()
                    .alarmId(msg.getAlarmId())
                    .title(msg.getTitle())
                    .body(msg.getBody())
                    .retryCount(nextRetry)
                    .build(), delay);
            log.warn("재시도 예약: alarmId={}, nextRetry={}, delayMs={}",
                    msg.getAlarmId(), nextRetry, delay);
        } else {
            if (alarm != null) {
                alarm.updateFailureReason("재시도 초과: " + e.getMessage());
            }
            log.error("최대 재시도 초과, 알림 실패 처리: alarmId={}", msg.getAlarmId());
        }
    }

    // 지수 백오프 기반으로 재시도 딜레이 시간(ms) 계산
    private long computeBackoffMillis(int retry) {
        switch (retry) {
            case 1: return 1 * 60_000L;   // 1분
            case 2: return 5 * 60_000L;   // 5분
            case 3: return 15 * 60_000L;  // 15분
            default: return 30 * 60_000L; // 30분
        }
    }


    // 알림 전송 결과 요약 데이터 (총 성공/실패/무효토큰/수신자 리스트) DTO
    private static class AlarmSendResultSummary {
        int totalSuccess;
        int totalFailure;
        List<String> invalidTokens;
        List<AlarmRecipient> recipients;

        AlarmSendResultSummary(int totalSuccess, int totalFailure, List<String> invalidTokens, List<AlarmRecipient> recipients) {
            this.totalSuccess = totalSuccess;
            this.totalFailure = totalFailure;
            this.invalidTokens = invalidTokens;
            this.recipients = recipients;
        }
    }
}
