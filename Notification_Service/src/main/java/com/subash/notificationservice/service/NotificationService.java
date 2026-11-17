package com.subash.notificationservice.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.subash.notificationservice.dto.Paging;
import com.subash.notificationservice.exception.NotificationNotFoundException;
import com.subash.notificationservice.model.Notification;
import com.subash.notificationservice.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

	private final NotificationRepository repo;
	private final MongoTemplate mongoTemplate;

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

	public void saveNotification(String userId, String orderId, String type, String message) {

		if (repo.existsByOrderIdAndType(orderId, type)) {
			log.info("Notification already exists: order {}, type {}", orderId, type);
			return;
		}

		// emailStatus PENDING: EmailSenderJob emails it later, outside the Kafka consumer
		Notification notification = Notification.builder().userId(userId).orderId(orderId).type(type).message(message)
				.createdAt(LocalDateTime.now()).read(false).emailStatus("PENDING")
				.nextEmailAttemptAt(LocalDateTime.now()).build();
		try {
			repo.save(notification);
			log.info("Notification saved: order {}, type {}", orderId, type);
		} catch (DuplicateKeyException e) {
			log.info("Duplicate notification skipped: order {}, type {}", orderId, type);
		}
	}

	public boolean hasNotification(String orderId, String type) {
		return repo.existsByOrderIdAndType(orderId, type);
	}

	// userId is stored as a String (String.valueOf of the Long in the Kafka event),
	// so the Long from the token is converted the same way before comparing
	// Newest first; id breaks ties so the order is stable across pages
	public Page<Notification> getByUser(Long userId, Pageable pageable) {
		return repo.findByUserId(String.valueOf(userId), Paging.of(pageable, NEWEST_FIRST));
	}

	public Page<Notification> getUnread(Long userId, Pageable pageable) {
		return repo.findByUserIdAndRead(String.valueOf(userId), false, Paging.of(pageable, NEWEST_FIRST));
	}

	// Marks all of this user's unread notifications as read in one update (only the "read" field)
	public void markAllRead(Long userId) {
		mongoTemplate.updateMulti(Query.query(Criteria.where("userId").is(String.valueOf(userId)).and("read").is(false)),
				new Update().set("read", true), Notification.class);
	}

	// Someone else's notification gives the same 404 as a missing one
	public void markRead(String id, Long userId) {
		repo.findById(id).filter(n -> String.valueOf(userId).equals(n.getUserId()))
				.orElseThrow(() -> new NotificationNotFoundException(id));
		// Set only "read": saving the whole document could overwrite the email fields
		// that EmailSenderJob updates at the same time (and resend the email)
		mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(id)), new Update().set("read", true),
				Notification.class);
	}
}
