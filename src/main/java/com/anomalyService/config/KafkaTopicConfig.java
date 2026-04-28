package com.anomalyService.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic storedLogsTopic() {
        return TopicBuilder.name("stored-logs")
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic anomaliesTopic() {
        return TopicBuilder.name("anomalies")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
