package com.subash.orderservice.exception;

// Product Service is down, timed out or returned an error -> 503
public class ProductServiceUnavailableException extends RuntimeException {

	public ProductServiceUnavailableException() {
		super("Product service unavailable, please try again");
	}
}
