package boombim_rabbitmq.domain.alarm.domain.entity.vote.place;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;

@Getter
@MappedSuperclass
public abstract class BaseExpiringEntity extends BaseEntity {

    @Column(name = "expires_at", nullable = false, columnDefinition = "timestamptz")
    private Instant expiresAt;

    protected Duration ttl() {
        return Duration.ofHours(1);
    }

    @PrePersist
    protected void setExpiresIfMissing() {
        if (expiresAt != null) {
            return;
        }

        Instant baseTime = getCreatedAt();
        if (baseTime == null) {
            baseTime = Instant.now();
        }
        expiresAt = baseTime.plus(ttl());
    }

    protected void setExpiresAt(
        Instant expiresAt
    ) {
        this.expiresAt = expiresAt;
    }

}
