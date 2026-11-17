package com.subash.notificationservice.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {

	private Long orderId;
	private Long userId;
	private BigDecimal totalAmount;
	private String status;
	private String cancellationReason;
}
