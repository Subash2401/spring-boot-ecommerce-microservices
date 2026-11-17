package com.subash.orderservice.exception;

// The order request is well-formed but breaks a business rule -> 400
public class InvalidOrderException extends RuntimeException {

	public InvalidOrderException(String message) {
		super(message);
	}
}
