package com.subash.notificationservice.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.subash.notificationservice.dto.OrderEvent;
import com.subash.notificationservice.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

	private final NotificationService notificationService;
	private final JsonMapper jsonMapper;

	@KafkaListener(topics = "${kafka.topic.order-events}", groupId = "${spring.kafka.consumer.group-id}")
	public void consume(String message) {
		// Unreadable JSON throws JacksonException; KafkaErrorConfig sends it to the DLT without retries
		OrderEvent event = jsonMapper.readValue(message, OrderEvent.class);

		if (event.getOrderId() == null || event.getUserId() == null) {
			log.warn("Skipping OrderEvent with missing ids: {}", message);
			return;
		}

		log.info("Received OrderEvent for orderId: {}", event.getOrderId());

		if ("CANCELLED".equals(event.getStatus())) {
			String orderId = String.valueOf(event.getOrderId());
			String text = switch (String.valueOf(event.getCancellationReason())) {
			case "USER_CANCELLED" -> "You cancelled order #" + orderId + ".";
			case "PAYMENT_TIMEOUT" -> "Order #" + orderId + " was cancelled because payment was not completed in time.";
			case "RESERVATION_TIMEOUT" -> "Order #" + orderId + " was cancelled: we couldn't confirm stock in time. Please try again.";
			default -> "Your order #" + orderId + " has been cancelled.";
			};
			notificationService.saveNotification(String.valueOf(event.getUserId()), orderId, "ORDER_CANCELLED", text);
		}
	}
}
