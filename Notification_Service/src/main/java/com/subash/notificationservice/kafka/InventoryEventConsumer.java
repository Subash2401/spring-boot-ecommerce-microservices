package com.subash.notificationservice.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.subash.notificationservice.dto.InventoryEvent;
import com.subash.notificationservice.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventConsumer {

	private final NotificationService notificationService;
	private final JsonMapper jsonMapper;

	@KafkaListener(topics = "${kafka.topic.inventory-events}", groupId = "${spring.kafka.consumer.group-id}")
	public void consume(String message) {
		// Unreadable JSON throws JacksonException; KafkaErrorConfig sends it to the DLT without retries
		InventoryEvent event = jsonMapper.readValue(message, InventoryEvent.class);
		if (event.getOrderId() == null || event.getUserId() == null) {
			log.warn("Skipping InventoryEvent with missing ids: {}", message);
			return;
		}
		String orderId = String.valueOf(event.getOrderId());
		String userId = String.valueOf(event.getUserId());

		if ("RESERVED".equals(event.getStatus())) {
			// A late RESERVED for an order that is already cancelled must not ask the user to pay
			if (notificationService.hasNotification(orderId, "ORDER_CANCELLED")) {
				log.info("Order {} already cancelled, skipping payment reminder", orderId);
				return;
			}
			notificationService.saveNotification(userId, orderId, "AWAITING_PAYMENT",
					"Order #" + orderId + " is reserved. Please complete payment within 15 minutes.");
		} else if ("REJECTED".equals(event.getStatus())) {
			notificationService.saveNotification(userId, orderId, "OUT_OF_STOCK",
					"Order #" + orderId + " was cancelled: not enough stock.");
		}
	}
}
