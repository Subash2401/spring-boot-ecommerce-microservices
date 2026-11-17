package com.subash.notificationservice.service;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.subash.notificationservice.model.Notification;
import com.subash.notificationservice.model.UserContact;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

// Emails notifications whose emailStatus is PENDING. It runs on its own schedule, so the
// Kafka consumers only save notifications and are never slowed down or broken by SMTP.
// A failed send is retried after 1, 2, 4, 8, 16 minutes; after 6 attempts it is FAILED.
// If the user's address is not known yet (their UserRegistered event has not arrived),
// it is retried the same way and ends as NO_ADDRESS.
// Each notification is unique per (orderId, type), so a user gets at most one email per
// notification; a crash between sending and saving SENT can send it twice (at-least-once).
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailSenderJob {

	private static final int MAX_ATTEMPTS = 6;
	private static final int BATCH_SIZE = 20;

	private final MongoTemplate mongoTemplate;
	private final JavaMailSender mailSender;

	@Value("${app.mail.from}")
	private String from;

	@Scheduled(fixedDelay = 10000)
	@SchedulerLock(name = "emailSender", lockAtMostFor = "PT5M")
	public void sendPendingEmails() {
		Query due = Query.query(Criteria.where("emailStatus").is("PENDING").and("nextEmailAttemptAt")
				.lte(LocalDateTime.now())).with(Sort.by("id")).limit(BATCH_SIZE);
		List<Notification> pending = mongoTemplate.find(due, Notification.class);
		for (Notification notification : pending) {
			sendOne(notification);
		}
	}

	private void sendOne(Notification notification) {
		UserContact contact = mongoTemplate.findOne(
				Query.query(Criteria.where("userId").is(Long.valueOf(notification.getUserId()))), UserContact.class);
		if (contact == null) {
			retryLater(notification, "NO_ADDRESS", "no email address for user " + notification.getUserId());
			return;
		}
		try {
			// One email with two versions of the same text: HTML, and plain text for mail apps
			// that do not show HTML ("multipart" = true). UTF-8 so that "₹" arrives correctly.
			MimeMessage mail = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mail, true, "UTF-8");
			helper.setFrom(from);
			helper.setTo(contact.getEmail());
			helper.setSubject(EmailContent.subject(notification));
			helper.setText(EmailContent.text(notification), EmailContent.html(notification));
			mailSender.send(mail);
		} catch (MailException | MessagingException e) {
			retryLater(notification, "FAILED", e.getMessage());
			return;
		}
		// Only the email fields are updated, so a concurrent "mark as read" is not overwritten
		mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(notification.getId())),
				new Update().set("emailStatus", "SENT").set("emailSentAt", LocalDateTime.now())
						.inc("emailAttempts", 1),
				Notification.class);
		log.info("Email sent for notification {} (order {}, {})", notification.getId(), notification.getOrderId(),
				notification.getType());
	}

	private void retryLater(Notification notification, String finalStatus, String reason) {
		int attempts = notification.getEmailAttempts() + 1;
		Update update = new Update().set("emailAttempts", attempts);
		if (attempts >= MAX_ATTEMPTS) {
			update.set("emailStatus", finalStatus);
			log.error("Giving up on email for notification {} after {} attempts: {}", notification.getId(), attempts,
					reason);
		} else {
			long waitMinutes = 1L << (attempts - 1); // 1, 2, 4, 8, 16
			update.set("nextEmailAttemptAt", LocalDateTime.now().plusMinutes(waitMinutes));
			log.warn("Email for notification {} not sent (attempt {}), retrying in {} min: {}", notification.getId(),
					attempts, waitMinutes, reason);
		}
		mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(notification.getId())), update,
				Notification.class);
	}
}
