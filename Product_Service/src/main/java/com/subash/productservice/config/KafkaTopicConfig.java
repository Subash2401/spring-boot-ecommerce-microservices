package com.subash.productservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

	@Bean
	public NewTopic inventoryEventsTopic(@Value("${kafka.topic.inventory-events:inventory-events}") String name,
			@Value("${kafka.topic.replicas:1}") int replicas) {
		return TopicBuilder.name(name).partitions(3).replicas(replicas).build();
	}

	@Bean
	public NewTopic inventoryEventsDltTopic(@Value("${kafka.topic.inventory-events:inventory-events}") String name,
			@Value("${kafka.topic.replicas:1}") int replicas) {
		return TopicBuilder.name(name + "-dlt").partitions(3).replicas(replicas).build();
	}
}
