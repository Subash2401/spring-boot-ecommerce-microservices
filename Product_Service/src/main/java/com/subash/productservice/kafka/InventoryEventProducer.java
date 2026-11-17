package com.subash.productservice.kafka;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

// Sends synchronously (waits up to 10 s for Kafka) and throws if the send fails. It is
// called from a Kafka listener, so the exception stops the offset commit and the
// OrderEvent is redelivered until the InventoryEvent is sent. No outbox is needed here:
// Product reacts to a Kafka message and answers on the same Kafka.
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventProducer {

	private static final long SEND_TIMEOUT_SECONDS = 10;

	private final KafkaTemplate<String, String> kafkaTemplate;
	private final JsonMapper jsonMapper;

	@Value("${kafka.topic.inventory-events}")
	private String inventoryEventsTopic;

	public void publish(InventoryEvent event) {
		String key = String.valueOf(event.getOrderId());
		String message = jsonMapper.writeValueAsString(event);
		try {
			kafkaTemplate.send(inventoryEventsTopic, key, message).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while sending inventory event for order " + key, e);
		} catch (ExecutionException | TimeoutException e) {
			throw new IllegalStateException("Could not send inventory event for order " + key, e);
		}
		log.info("Inventory event {} published for orderId: {}", event.getStatus(), key);
	}
}
