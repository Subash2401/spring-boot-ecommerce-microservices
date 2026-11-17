package com.subash.notificationservice.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// From user-events (published by User Service), keyed by userId
@Getter
@Setter
@NoArgsConstructor
public class UserEvent {

	private Long userId;
	private String email;
	private String type;
}
