package com.subash.paymentservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

	private Long id;
	private Long orderId;
	private Long userId;
	private BigDecimal amount;
	private String status;
	private String transactionId;
	private LocalDateTime createdAt;
	// Deadline to pay: createdAt + payment.window-minutes. An Instant, so the JSON has a "Z" (UTC)
	// and the browser can count down correctly in any time zone
	private Instant expiresAt;
}
