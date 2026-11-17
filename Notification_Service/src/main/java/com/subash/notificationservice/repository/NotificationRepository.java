package com.subash.notificationservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.subash.notificationservice.model.Notification;

public interface NotificationRepository extends MongoRepository<Notification, String> {
	Page<Notification> findByUserId(String userId, Pageable pageable);
	Page<Notification> findByUserIdAndRead(String userId, boolean read, Pageable pageable);
	boolean existsByOrderIdAndType(String orderId, String type);
}
