package boombim_rabbitmq.domain.alarm.domain.repository.alarm;


import boombim_rabbitmq.domain.alarm.domain.entity.alarm.AlarmRecipient;
import boombim_rabbitmq.domain.alarm.domain.entity.alarm.fcm.type.DeviceType;
import boombim_rabbitmq.domain.alarm.domain.entity.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlarmRecipientRepository extends JpaRepository<AlarmRecipient, Long> {
    List<AlarmRecipient> findAllByMemberAndDeviceTypeOrderByCreatedAtAsc(
            Member user, DeviceType deviceType);
}
