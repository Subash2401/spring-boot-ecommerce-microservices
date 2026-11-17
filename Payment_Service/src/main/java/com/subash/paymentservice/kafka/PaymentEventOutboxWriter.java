package com.subash.paymentservice.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.subash.paymentservice.dto.PaymentEvent;
import com.subash.paymentservice.model.OutboxEvent;
import com.subash.paymentservice.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// Runs just before the payment's transaction commits, so the payment change and its
// outbox row are saved together or not at all.
@Component
@RequiredArgsConstructor
public class PaymentEventOutboxWriter {

	private final OutboxEventRepository outboxEventRepository;
	private final JsonMapper jsonMapper;

	@Value("${kafka.topic.payment-events}")
	private String paymentEventsTopic;

	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	public void saveToOutbox(PaymentEvent event) {
		String payload;
		try {
			payload = jsonMapper.writeValueAsString(event);
		} catch (JacksonException e) {
			throw new IllegalStateException("Could not serialize PaymentEvent " + event.getOrderId(), e);
		}
		outboxEventRepository.save(new OutboxEvent(paymentEventsTopic, String.valueOf(event.getOrderId()), payload));
	}
}
