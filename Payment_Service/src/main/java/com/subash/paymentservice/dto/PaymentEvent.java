package com.subash.paymentservice.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEvent {

	private Long orderId;
	private Long userId;
	private BigDecimal amount;
	private String status;
	private String transactionId;
}
