package com.subash.orderservice.kafka;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryEvent {
	private Long orderId;
	private Long userId;
	private BigDecimal totalAmount;
	private String status;
	private String reason;
}
