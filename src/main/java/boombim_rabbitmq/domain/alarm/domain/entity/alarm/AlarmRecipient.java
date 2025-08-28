package boombim_rabbitmq.domain.alarm.domain.entity.alarm;

import boombim_rabbitmq.domain.alarm.domain.entity.alarm.fcm.type.DeviceType;
import boombim_rabbitmq.domain.alarm.domain.entity.alarm.type.DeliveryStatus;
import boombim_rabbitmq.domain.alarm.domain.entity.member.Member;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class AlarmRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alarm_id", nullable = false)
    private Alarm alarm;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeviceType deviceType; // ANDROID / IOS

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING; // PENDING/SENT/FAILED/READ 등

    private LocalDateTime sentAt;
    private LocalDateTime readAt;

    @Column(columnDefinition = "TEXT")
    private String failureReason;


    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public AlarmRecipient(Alarm alarm, Member member, DeviceType deviceType) {
        this.alarm = alarm;
        this.member = member;
        this.deviceType = deviceType;
    }

    public void markSent() {
        this.deliveryStatus = DeliveryStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    public void markFailed(String reason) {
        this.deliveryStatus = DeliveryStatus.FAILED;
        this.failureReason = reason;
    }

    // 읽음 처리
    public void updateDeliveryStatus() {
        this.deliveryStatus = DeliveryStatus.READ;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(); // 한국시간
        }

    }

}
