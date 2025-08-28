package boombim_rabbitmq.domain.alarm.domain.repository.member;



import boombim_rabbitmq.domain.alarm.domain.entity.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, String> {
    Optional<Member> findByEmail(String email);

}
