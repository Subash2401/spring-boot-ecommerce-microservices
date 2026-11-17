package com.subash.productservice.kafka;

import java.math.BigDecimal;
import java.util.List;

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
	private List<OrderEventItem> items;
}