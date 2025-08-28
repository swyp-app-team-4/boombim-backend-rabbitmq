package boombim_rabbitmq.domain.alarm.domain.repository.vote;

import boombim_rabbitmq.domain.alarm.domain.entity.member.Member;
import boombim_rabbitmq.domain.alarm.domain.entity.vote.Vote;
import boombim_rabbitmq.domain.alarm.domain.entity.vote.VoteAnswer;
import boombim_rabbitmq.domain.alarm.domain.entity.vote.type.VoteAnswerType;
import feign.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface VoteAnswerRepository extends JpaRepository<VoteAnswer, Long> {
    Optional<VoteAnswer> findByMemberAndVote(Member user, Vote vote);

    List<VoteAnswer> findByMember(Member member);

    List<VoteAnswer> findByVote(Vote vote);

    @Query("select va.member from VoteAnswer va where va.vote = :vote")
    List<Member> findMembersByVote(@Param("vote") Vote vote);

    interface TypeCount { VoteAnswerType getType(); long getCnt(); }

    @Query("""
       select va.answerType as type, count(va) as cnt
       from VoteAnswer va
       where va.vote.id = :voteId
       group by va.answerType
    """)
    List<TypeCount> countByType(@Param("voteId") Long voteId);
}
