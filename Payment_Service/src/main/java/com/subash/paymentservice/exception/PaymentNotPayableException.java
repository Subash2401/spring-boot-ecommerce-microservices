package com.subash.paymentservice.exception;

public class PaymentNotPayableException extends RuntimeException {
	public PaymentNotPayableException(Long orderId) {
		super("Payment for order " + orderId + " is not pending");
	}

	public PaymentNotPayableException(Long orderId, String reason) {
		super("Payment for order " + orderId + " cannot be made: " + reason);
	}
}
