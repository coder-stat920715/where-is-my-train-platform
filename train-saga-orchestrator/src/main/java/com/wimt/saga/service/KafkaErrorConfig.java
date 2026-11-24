package com.wimt.saga.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.core.KafkaAdmin.NewTopics;
import org.springframework.util.backoff.FixedBackOff;
import com.wimt.common.Topics;

@Configuration
public class KafkaErrorConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), new FixedBackOff(500L, 3L));
    }

    /** Saga command/reply channels, partitioned by train key so a journey is processed in order. */
    @Bean
    public NewTopics sagaTopics() {
        return new NewTopics(
                TopicBuilder.name(Topics.SAGA_ETA_COMMANDS).partitions(6).replicas(1).build(),
                TopicBuilder.name(Topics.SAGA_BOARD_COMMANDS).partitions(6).replicas(1).build(),
                TopicBuilder.name(Topics.SAGA_TELEMETRY_COMMANDS).partitions(6).replicas(1).build(),
                TopicBuilder.name(Topics.SAGA_REPLIES).partitions(6).replicas(1).build());
    }
}
