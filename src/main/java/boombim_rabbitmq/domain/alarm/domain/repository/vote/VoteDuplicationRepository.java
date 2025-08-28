package boombim_rabbitmq.domain.alarm.domain.repository.vote;

import boombim_rabbitmq.domain.alarm.domain.entity.member.Member;
import boombim_rabbitmq.domain.alarm.domain.entity.vote.Vote;
import boombim_rabbitmq.domain.alarm.domain.entity.vote.VoteDuplication;
import feign.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;


public interface VoteDuplicationRepository extends JpaRepository<VoteDuplication, Long> {
    List<VoteDuplication> findByMember(Member member);

    @Query("select vd.member from VoteDuplication vd where vd.vote = :vote")
    List<Member> findMembersByVote(@Param("vote") Vote vote);
}
