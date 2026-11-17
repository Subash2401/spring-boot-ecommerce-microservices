package com.subash.notificationservice.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Document(collection = "notifications")
@CompoundIndex(name = "order_type_unique", def = "{'orderId':1, 'type':1}", unique = true)
// For the paged "my notifications" lists (userId, then newest first)
@CompoundIndex(name = "user_created", def = "{'userId':1, 'createdAt':-1}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

	@Id
	private String id;
	private String userId;
	private String orderId;
	private String type;
	private String message;
	private LocalDateTime createdAt;
	private boolean read;

	// Email delivery, internal only (not shown in the API). Each notification is its own
	// email queue entry: EmailSenderJob sends PENDING ones. Notifications from before email
	// existed have no status and are never emailed.
	@JsonIgnore
	private String emailStatus; // PENDING, SENT, FAILED or NO_ADDRESS
	@JsonIgnore
	private int emailAttempts;
	@JsonIgnore
	private LocalDateTime nextEmailAttemptAt;
	@JsonIgnore
	private LocalDateTime emailSentAt;
}
