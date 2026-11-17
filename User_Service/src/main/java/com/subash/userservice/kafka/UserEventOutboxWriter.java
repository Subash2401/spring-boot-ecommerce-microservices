package com.subash.userservice.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.subash.userservice.entity.OutboxEvent;
import com.subash.userservice.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// Runs just before the user's transaction commits, so the user row and its outbox row
// are saved together or not at all. The key is the userId (user events are about a user,
// not an order).
@Component
@RequiredArgsConstructor
public class UserEventOutboxWriter {

	private final OutboxEventRepository outboxEventRepository;
	private final JsonMapper jsonMapper;

	@Value("${kafka.topic.user-events:user-events}")
	private String userEventsTopic;

	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	public void saveToOutbox(UserEvent event) {
		String payload;
		try {
			payload = jsonMapper.writeValueAsString(event);
		} catch (JacksonException e) {
			throw new IllegalStateException("Could not serialize UserEvent " + event.getUserId(), e);
		}
		outboxEventRepository.save(new OutboxEvent(userEventsTopic, String.valueOf(event.getUserId()), payload));
	}
}
