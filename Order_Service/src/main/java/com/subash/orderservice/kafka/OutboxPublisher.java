package com.subash.orderservice.kafka;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.subash.orderservice.model.OutboxEvent;
import com.subash.orderservice.model.OutboxStatus;
import com.subash.orderservice.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

	private static final long MAX_BACKOFF_SECONDS = 30;

	private final OutboxEventRepository outboxEventRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;

	private volatile int consecutiveFailures = 0;
	private volatile Instant nextAttemptAt = Instant.MIN;

	@Scheduled(fixedDelay = 1000)
	@SchedulerLock(name = "outboxPublisher", lockAtMostFor = "PT30S")
	public void publishPending() {
		if (Instant.now().isBefore(nextAttemptAt)) {
			return;
		}

		List<OutboxEvent> events = outboxEventRepository.findTop100ByStatusOrderByIdAsc(OutboxStatus.PENDING);

		for (OutboxEvent event : events) {
			try {
				kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload()).get(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			} catch (Exception e) {
				onFailure(event, e);
				return;
			}

			onSuccess();
			event.setStatus(OutboxStatus.SENT);
			event.setSentAt(LocalDateTime.now());
			outboxEventRepository.save(event);
			log.info("Outbox event {} sent to {}", event.getId(), event.getTopic());
		}
	}

	private void onFailure(OutboxEvent event, Exception e) {
		consecutiveFailures++;
		long backoff = Math.min(MAX_BACKOFF_SECONDS, 1L << Math.min(consecutiveFailures, 5));
		nextAttemptAt = Instant.now().plusSeconds(backoff);

		if (consecutiveFailures == 1 || consecutiveFailures % 10 == 0) {
			log.warn("Outbox event {} not sent (failure #{}), retrying in {}s: {}", event.getId(), consecutiveFailures,
					backoff, e.getMessage());
		}
	}

	private void onSuccess() {
		if (consecutiveFailures > 0) {
			log.info("Kafka reachable again after {} failed attempts", consecutiveFailures);
			consecutiveFailures = 0;
			nextAttemptAt = Instant.MIN;
		}
	}
}