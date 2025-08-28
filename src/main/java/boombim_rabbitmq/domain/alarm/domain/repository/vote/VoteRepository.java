package boombim_rabbitmq.domain.alarm.domain.repository.vote;



import boombim_rabbitmq.domain.alarm.domain.entity.member.Member;
import boombim_rabbitmq.domain.alarm.domain.entity.vote.Vote;
import boombim_rabbitmq.domain.alarm.domain.entity.vote.type.VoteStatus;
import feign.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VoteRepository extends JpaRepository<Vote, Long> {
    Optional<Vote> findByPosIdAndIsVoteActivateTrue(String posId);

    List<Vote> findByMember(Member user);

    // 위도/경도 바운딩 박스 안에 들어오는 후보만 가져오기
    @Query("SELECT v FROM Vote v " +
            "WHERE v.latitude BETWEEN :latMin AND :latMax " +
            "AND v.longitude BETWEEN :lonMin AND :lonMax")
    List<Vote> findAllInBoundingBox(double latMin, double latMax,
                                    double lonMin, double lonMax);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Vote v
           set v.voteStatus = :end, v.isVoteActivate = false
         where v.voteStatus = :progress
         and v.isVoteActivate = true
           and v.endTime <= :now
    """)
    int bulkCloseExpired(@Param("progress") VoteStatus progress,
                         @Param("end") VoteStatus end,
                         @Param("now") LocalDateTime now);

    @Query("select v.member from Vote v where v = :vote")
    List<Member> findMembersByVote(@Param("vote") Vote vote);

    List<Vote> findByVoteStatusAndEndTimeLessThanEqual(VoteStatus status, LocalDateTime now);

    List<Vote> findByPassivityAlarmFlagTrue();


}
