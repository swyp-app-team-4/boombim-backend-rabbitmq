package boombim_rabbitmq.domain.alarm.domain.entity.vote;



import boombim_rabbitmq.domain.alarm.domain.entity.member.Member;
import boombim_rabbitmq.domain.alarm.domain.entity.vote.type.VoteAnswerType;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@Getter
@Entity
@NoArgsConstructor
@DynamicUpdate
public class VoteAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vote_id", nullable = false)
    private Vote vote;

    // 투표 타입
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VoteAnswerType answerType;


    @Builder
    public VoteAnswer(Member member, Vote vote, VoteAnswerType answerType){
        this.member=member;
        this.vote=vote;
        this.answerType=answerType;
    }

}

