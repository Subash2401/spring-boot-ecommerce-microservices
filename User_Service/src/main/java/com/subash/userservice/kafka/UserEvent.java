package com.subash.userservice.kafka;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Published to user-events, keyed by userId. Notification keeps its own userId -> email
// copy from these events, so it can email users without calling User Service.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserEvent {

	public static final String USER_REGISTERED = "USER_REGISTERED";

	private Long userId;
	private String email;
	private String type;
}
