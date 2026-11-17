package com.subash.notificationservice.kafka;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.subash.notificationservice.dto.PaymentEvent;
import com.subash.notificationservice.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

	private final NotificationService notificationService;
	private final JsonMapper jsonMapper;

	@KafkaListener(topics = "${kafka.topic.payment-events}", groupId = "${spring.kafka.consumer.group-id}")
	public void consume(String message) {
		// Unreadable JSON throws JacksonException; KafkaErrorConfig sends it to the DLT without retries
		PaymentEvent event = jsonMapper.readValue(message, PaymentEvent.class);

		if (event.getOrderId() == null || event.getUserId() == null) {
			log.warn("Skipping PaymentEvent with missing ids: {}", message);
			return;
		}

		log.info("Received PaymentEvent for orderId: {}", event.getOrderId());

		String orderId = String.valueOf(event.getOrderId());
		String userId = String.valueOf(event.getUserId());

		if ("SUCCESS".equals(event.getStatus())) {
			// An order cancelled before its payment was processed gets REFUNDED next, so do not
			// tell the user it was placed. (If the cancel arrives after this event, both notices
			// appear: topics are not ordered against each other, see docs/HOW-IT-WORKS.md.)
			if (notificationService.hasNotification(orderId, "ORDER_CANCELLED")) {
				log.info("Order {} already cancelled, skipping ORDER_PLACED", orderId);
				return;
			}
			notificationService.saveNotification(userId, orderId, "ORDER_PLACED",
					"Your order #" + orderId + " has been placed successfully. Amount paid: " + rupees(event.getAmount()));
		} else if ("FAILED".equals(event.getStatus())) {
			notificationService.saveNotification(userId, orderId, "PAYMENT_FAILED",
					"Payment failed for order #" + orderId + ". Your order was not placed.");
		} else if ("REFUNDED".equals(event.getStatus())) {
			// Safety net in Payment: the order was cancelled after it was paid
			notificationService.saveNotification(userId, orderId, "PAYMENT_REFUNDED",
					"Order #" + orderId + " was cancelled and your payment of " + rupees(event.getAmount()) + " has been refunded.");
		}
	}

	// 999.5 -> "₹999.50", 125000 -> "₹1,25,000.00" (Indian grouping). A new NumberFormat
	// each time, because NumberFormat is not thread-safe.
	private static String rupees(BigDecimal amount) {
		if (amount == null) {
			return "the full amount";
		}
		return NumberFormat.getCurrencyInstance(Locale.of("en", "IN")).format(amount);
	}
}
