package com.subash.userservice.exception;

// Unknown, expired, revoked or reused refresh token -> 401 (the user must log in again)
public class InvalidRefreshTokenException extends RuntimeException {

	public InvalidRefreshTokenException() {
		super("Refresh token is invalid or expired, please log in again");
	}
}
