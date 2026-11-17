package com.subash.paymentservice.model;

public enum PaymentStatus {
	
	PENDING,
	// Claimed by one pay request; the gateway is being charged. Resolved by pay() or PaymentRecoveryJob
	PROCESSING,
	SUCCESS,
	FAILED,
	// The order was cancelled before any payment existed
	CANCELLED,
	// Not paid within the payment window (PaymentExpiryJob)
	EXPIRED,
	// Charged, but the order had been cancelled: the safety net refunds it (mock refund)
	REFUNDED;
}
