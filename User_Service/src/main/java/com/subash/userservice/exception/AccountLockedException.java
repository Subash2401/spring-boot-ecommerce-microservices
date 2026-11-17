package com.subash.userservice.exception;

// Too many wrong passwords in a row -> 423 until lockedUntil has passed
public class AccountLockedException extends RuntimeException {

	public AccountLockedException() {
		super("Account locked, try again later");
	}
}
