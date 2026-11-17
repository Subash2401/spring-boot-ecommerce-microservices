package com.subash.paymentservice.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.subash.paymentservice.dto.OrderEvent;
import com.subash.paymentservice.service.PaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

	private final PaymentService paymentService;
	private final JsonMapper jsonMapper;

	@KafkaListener(topics = "${kafka.topic.order-events}", groupId = "${spring.kafka.consumer.group-id}")

	public void consume(String message) {
		// Unreadable JSON throws JacksonException; KafkaErrorConfig sends it to the DLT without retries
		OrderEvent event = jsonMapper.readValue(message, OrderEvent.class);
		if ("CANCELLED".equals(event.getStatus())) {
			paymentService.handleCancellation(event);
		}
	}

}
