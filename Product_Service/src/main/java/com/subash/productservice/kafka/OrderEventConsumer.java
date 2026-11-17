package com.subash.productservice.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.subash.productservice.model.Reservation;
import com.subash.productservice.service.InventoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

	private final InventoryService inventoryService;
	private final InventoryEventProducer inventoryEventProducer;
	private final JsonMapper jsonMapper;

	@KafkaListener(topics = "${kafka.topic.order-events}", groupId = "${spring.kafka.consumer.group-id}")
	public void consume(String message) {
		// Unreadable JSON throws JacksonException; KafkaErrorConfig sends it to the DLT without retries
		OrderEvent event = jsonMapper.readValue(message, OrderEvent.class);

		if ("CANCELLED".equals(event.getStatus())) {
			inventoryService.releaseForOrder(event.getOrderId());
			return;
		}
		if (!"PENDING".equals(event.getStatus())) {
			return;
		}
		if (event.getItems() == null || event.getItems().isEmpty()) {
			log.warn("Skipping OrderEvent {} with no items", event.getOrderId());
			return;
		}
		// The first delivery decides; a redelivered event re-sends the same decision.
		// Every step is safe to repeat (see InventoryService), and publish() waits for Kafka,
		// so the offset is committed only after the InventoryEvent is really sent.
		Reservation reservation = inventoryService.startOrResume(event.getOrderId(), event.getItems());
		String result = reservation.getStatus();
		if ("RESERVING".equals(result)) {
			boolean reserved = inventoryService.reserve(event.getOrderId(), event.getItems());
			result = reserved ? "RESERVED" : "REJECTED";
			inventoryService.markReservation(event.getOrderId(), result);
		} else if (!"RESERVED".equals(result) && !"REJECTED".equals(result)) {
			log.info("Order {} is already {}, nothing to send", event.getOrderId(), result);
			return;
		}

		inventoryEventProducer.publish(new InventoryEvent(event.getOrderId(), event.getUserId(), event.getTotalAmount(),
				result, "REJECTED".equals(result) ? "OUT_OF_STOCK" : null));
	}
}
