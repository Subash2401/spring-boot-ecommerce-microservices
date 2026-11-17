package com.subash.userservice.kafka;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.subash.userservice.entity.User;
import com.subash.userservice.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Users who registered before user-events existed have no UserRegistered event yet.
// At startup, write one to the outbox for every such user and mark them, in one
// transaction. After the first run there is nothing left, so later startups do nothing.
// If two instances start at once, a user may get two events; Notification stores emails
// by userId (upsert), so that is harmless.
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventBackfill implements ApplicationRunner {

	private final UserRepository userRepository;
	private final ApplicationEventPublisher eventPublisher;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		List<User> users = userRepository.findByEventPublishedFalse();
		for (User user : users) {
			eventPublisher.publishEvent(new UserEvent(user.getId(), user.getEmail(), UserEvent.USER_REGISTERED));
			user.setEventPublished(true);
		}
		if (!users.isEmpty()) {
			log.info("Backfill: wrote UserRegistered events for {} existing users", users.size());
		}
	}
}
