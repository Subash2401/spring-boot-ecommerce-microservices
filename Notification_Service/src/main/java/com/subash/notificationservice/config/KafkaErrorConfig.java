package com.subash.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;

@Configuration
@Slf4j
public class KafkaErrorConfig {

	@Bean
	public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> kafkaTemplate) {
		DeadLetterPublishingRecoverer deadLetterPublisher = new DeadLetterPublishingRecoverer(kafkaTemplate);
		// Log one clear ERROR line before a message is parked in <topic>-dlt, so it is never
		// moved there silently. docs/DEAD-LETTER-TOPICS.md shows how to replay it.
		ConsumerRecordRecoverer recoverer = (record, exception) -> {
			Throwable cause = NestedExceptionUtils.getMostSpecificCause(exception);
			log.error("Sending message to {}-dlt: partition={} offset={} key={} cause={}: {}", record.topic(),
					record.partition(), record.offset(), record.key(), cause.getClass().getSimpleName(),
					cause.getMessage());
			deadLetterPublisher.accept(record, exception);
		};
		DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
		// Unreadable JSON will never parse, so send it straight to the DLT instead of retrying
		errorHandler.addNotRetryableExceptions(JacksonException.class);
		return errorHandler;
	}
}
