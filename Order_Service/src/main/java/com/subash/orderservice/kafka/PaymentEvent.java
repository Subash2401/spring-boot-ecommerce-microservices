package com.subash.orderservice.kafka;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {

	private Long userId;
	private Long orderId;
	private BigDecimal amount;
	private String status;
	private String transactionId;
}
