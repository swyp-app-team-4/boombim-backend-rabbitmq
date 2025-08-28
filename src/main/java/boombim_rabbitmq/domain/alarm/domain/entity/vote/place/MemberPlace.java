package boombim_rabbitmq.domain.alarm.domain.entity.vote.place;


import boombim_rabbitmq.domain.alarm.domain.entity.vote.Vote;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(name = "member_places")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberPlace extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "memberPlace", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Vote> votes  = new ArrayList<>();

    @Column(name = "uuid", length = 32, nullable = false, unique = true)
    private String uuid;

    @Column(name = "name", length = 32, nullable = false)
    private String name;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Builder
    private MemberPlace(
        String uuid,
        String name,
        Double latitude,
        Double longitude
    ) {
        this.uuid = uuid;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public static MemberPlace of(
        String uuid,
        String name,
        Double latitude,
        Double longitude
    ) {
        return MemberPlace.builder()
            .uuid(uuid)
            .name(name)
            .latitude(latitude)
            .longitude(longitude)
            .build();
    }

}
