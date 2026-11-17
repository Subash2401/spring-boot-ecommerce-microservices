package com.subash.userservice.config;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.subash.userservice.entity.Role;
import com.subash.userservice.entity.User;
import com.subash.userservice.kafka.UserEvent;
import com.subash.userservice.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Creates the first admin at startup from ADMIN_EMAIL / ADMIN_PASSWORD (in .env), so a fresh
// install never needs a manual database edit. It only ever creates a NEW user, and only while
// no admin exists: it never changes roles on an existing user, and does nothing once an
// admin exists. Leave the variables empty to switch it off.
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements ApplicationRunner {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final ApplicationEventPublisher eventPublisher;

	@Value("${app.admin.email:}")
	private String adminEmail;

	@Value("${app.admin.password:}")
	private String adminPassword;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (userRepository.existsByRole(Role.ADMIN)) {
			return;
		}
		if (adminEmail.isBlank() || adminPassword.isBlank()) {
			log.warn("No admin user exists and ADMIN_EMAIL / ADMIN_PASSWORD are not set");
			return;
		}
		if (adminPassword.length() < 8 || adminPassword.length() > 72) {
			log.error("ADMIN_PASSWORD must be 8 to 72 characters; admin not created");
			return;
		}
		if (userRepository.findByEmail(adminEmail).isPresent()) {
			log.error("ADMIN_EMAIL belongs to an existing user; roles of existing users are never changed, admin not created");
			return;
		}
		User admin = User.builder().name("Administrator").email(adminEmail)
				.password(passwordEncoder.encode(adminPassword)).roles(Set.of(Role.ADMIN, Role.USER))
				.eventPublished(true).build();
		User saved = userRepository.save(admin);
		// Notification gets the admin's email address like any other user's
		eventPublisher.publishEvent(new UserEvent(saved.getId(), saved.getEmail(), UserEvent.USER_REGISTERED));
		log.info("Created the first admin user (id {})", saved.getId());
	}
}
