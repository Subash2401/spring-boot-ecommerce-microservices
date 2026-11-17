package com.subash.orderservice.kafka;

import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;

import com.subash.orderservice.service.OrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

	private final OrderService orderService;
	private final JsonMapper jsonMapper;

	@RetryableTopic(attempts = "4", backOff = @BackOff(delay = 30000, multiplier = 2.0), numPartitions = "3", replicationFactor = "1",
			exclude = JacksonException.class)
	@KafkaListener(topics = "${kafka.topic.payment-events}", groupId = "${spring.kafka.consumer.group-id}")
	public void consume(String message) {
		// Unreadable JSON throws JacksonException; exclude above sends it to the DLT without retries
		PaymentEvent event = jsonMapper.readValue(message, PaymentEvent.class);

		if (event.getOrderId() == null || event.getUserId() == null) {
			log.warn("Skipping PaymentEvent with missing ids: {}", message);
			return;
		}

		log.info("Received PaymentEvent for orderId: {}", event.getOrderId());

		orderService.handlePaymentResult(event.getOrderId(), event.getStatus());
	}
}
