package com.subash.notificationservice.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.subash.notificationservice.model.Notification;
import com.subash.notificationservice.service.NotificationService;

import lombok.RequiredArgsConstructor;

@RestController
// Every endpoint needs a logged-in user (second layer behind the URL rules); ownership is checked in the service
@PreAuthorize("isAuthenticated()")
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService service;

	@GetMapping("/me")
	// ?page=0&size=20 (size at most 50); newest first
	public ResponseEntity<PagedModel<Notification>> getAll(@AuthenticationPrincipal Long userId,
			@PageableDefault(size = 20) Pageable pageable){
		return ResponseEntity.ok(new PagedModel<>(service.getByUser(userId, pageable)));
	}

	@GetMapping("/me/unread")
	public ResponseEntity<PagedModel<Notification>> getUnread(@AuthenticationPrincipal Long userId,
			@PageableDefault(size = 20) Pageable pageable){
		return ResponseEntity.ok(new PagedModel<>(service.getUnread(userId, pageable)));
	}

	// Marks every unread notification of the logged-in user as read
	@PutMapping("/me/read-all")
	public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal Long userId) {
		service.markAllRead(userId);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{id}/read")
	public ResponseEntity<Void> markRead(@PathVariable String id, @AuthenticationPrincipal Long userId) {
		service.markRead(id, userId);
		return ResponseEntity.noContent().build();
	}
}
