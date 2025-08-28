package boombim_rabbitmq.domain.alarm.domain.entity.member;

public enum SocialProvider {
    KAKAO("kakao"),
    NAVER("naver"),
    APPLE("apple");
    private final String value;

    SocialProvider(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}