package com.subash.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// JSON body of /users/login and /users/refresh.
// accessToken: JWT for "Authorization: Bearer ...", valid expiresIn seconds (30 min).
// The refresh token is NOT here: it is set as an HttpOnly cookie (see UserController),
// so JavaScript in the browser can never read it.
@Getter
@AllArgsConstructor
public class LoginResponse {

	private String accessToken;
	private String tokenType;
	private long expiresIn;
}
