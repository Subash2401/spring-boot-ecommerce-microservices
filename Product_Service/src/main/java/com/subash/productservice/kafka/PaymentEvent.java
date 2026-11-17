package com.subash.productservice.kafka;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {
	private Long orderId;
	private Long userId;
	private BigDecimal amount;
	private String status;
	private String transactionId;
}
