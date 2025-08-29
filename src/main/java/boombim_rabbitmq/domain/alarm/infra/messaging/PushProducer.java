package boombim_rabbitmq.domain.alarm.infra.messaging;

import boombim_rabbitmq.domain.alarm.application.messaging.EndVoteMessage;
import boombim_rabbitmq.domain.alarm.application.messaging.NotifyMessage;
import boombim_rabbitmq.global.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class PushProducer {

    private final RabbitTemplate rabbitTemplate;

    // 재시도 큐로 지연 발행 (per-message TTL)
    public void publishRetry(NotifyMessage msg, long delayMillis) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_PUSH,
                RabbitMQConfig.RK_NOTIFY_RETRY,
                msg,
                m -> {
                    m.getMessageProperties().setExpiration(String.valueOf(delayMillis));
                    return m;
                }
        );
    }
}
