package com.subash.notificationservice.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Notification's own copy of each user's email address, filled from user-events.
// Lets it email a user without calling User Service. One document per user.
@Document(collection = "user_contacts")
@Getter
@Setter
@NoArgsConstructor
public class UserContact {

	@Id
	private String id;
	@Indexed(unique = true)
	private Long userId;
	private String email;
	private LocalDateTime updatedAt;
}
