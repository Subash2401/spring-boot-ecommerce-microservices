package com.subash.orderservice.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.subash.orderservice.model.OutboxEvent;
import com.subash.orderservice.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class OrderEventOutboxWriter {

	private final OutboxEventRepository outboxEventRepository;
	private final JsonMapper jsonMapper;

	@Value("${kafka.topic.order-events}")
	private String orderEventsTopic;

	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	public void saveToOutbox(OrderEvent event) {
		String payload;
		try {
			payload = jsonMapper.writeValueAsString(event);
		} catch (JacksonException e) {
			throw new IllegalStateException("Could not serialize OrderEvent " + event.getOrderId(), e);
		}
		outboxEventRepository.save(new OutboxEvent(orderEventsTopic, String.valueOf(event.getOrderId()), payload));
	}
}