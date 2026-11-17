package com.subash.notificationservice.kafka;

import java.time.LocalDateTime;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.subash.notificationservice.dto.UserEvent;
import com.subash.notificationservice.model.UserContact;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

// Keeps user_contacts (userId -> email) up to date. "earliest": this consumer group starts
// at the beginning of user-events, so it also gets the events User Service backfilled for
// users who registered before this topic existed.
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventConsumer {

	private final MongoTemplate mongoTemplate;
	private final JsonMapper jsonMapper;

	@KafkaListener(topics = "${kafka.topic.user-events:user-events}", groupId = "${spring.kafka.consumer.group-id}",
			properties = "auto.offset.reset=earliest")
	public void consume(String message) {
		// Unreadable JSON throws JacksonException; KafkaErrorConfig sends it to the DLT without retries
		UserEvent event = jsonMapper.readValue(message, UserEvent.class);
		if (event.getUserId() == null || event.getEmail() == null) {
			log.warn("Skipping UserEvent with missing userId or email");
			return;
		}
		// Upsert: one atomic operation, so a duplicate event just writes the same values again
		mongoTemplate.upsert(Query.query(Criteria.where("userId").is(event.getUserId())),
				new Update().set("email", event.getEmail()).set("updatedAt", LocalDateTime.now()), UserContact.class);
		log.info("Stored email address for user {}", event.getUserId());
	}
}
