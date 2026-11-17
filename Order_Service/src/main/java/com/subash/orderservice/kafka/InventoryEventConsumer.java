package com.subash.orderservice.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.subash.orderservice.service.OrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventConsumer {

	private final OrderService orderService;
	private final JsonMapper jsonMapper;

	@KafkaListener(topics = "${kafka.topic.inventory-events}", groupId = "${spring.kafka.consumer.group-id}")
	public void consume(String message) {
		// Unreadable JSON throws JacksonException; KafkaErrorConfig sends it to the DLT without retries
		InventoryEvent event = jsonMapper.readValue(message, InventoryEvent.class);
		orderService.handleInventoryResult(event.getOrderId(), event.getStatus());
	}
}
