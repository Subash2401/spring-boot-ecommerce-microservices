package com.subash.productservice.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.subash.productservice.service.InventoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

	private final InventoryService inventoryService;
	private final JsonMapper jsonMapper;

	@KafkaListener(topics = "${kafka.topic.payment-events}", groupId = "${spring.kafka.consumer.group-id}")
	public void consume(String message) {
		// Unreadable JSON throws JacksonException; KafkaErrorConfig sends it to the DLT without retries
		PaymentEvent event = jsonMapper.readValue(message, PaymentEvent.class);
		switch (String.valueOf(event.getStatus())) {
		// Paid: the held stock is sold (holds stay, marked paid, in case of a refund)
		case "SUCCESS" -> inventoryService.confirmForOrder(event.getOrderId());
		// Not paid: give the stock back. Order also sends CANCELLED; releasing twice is a no-op
		case "FAILED", "EXPIRED" -> inventoryService.releaseForOrder(event.getOrderId());
		// REFUNDED needs nothing here: the order's CANCELLED event gives the stock back
		default -> {
		}
		}
	}
}
