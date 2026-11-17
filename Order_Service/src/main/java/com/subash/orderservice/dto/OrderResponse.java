package com.subash.orderservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.subash.orderservice.model.CancellationReason;
import com.subash.orderservice.model.OrderStatus;

import lombok.Data;

@Data
public class OrderResponse {

	private Long id;
	private Long userId;
	private OrderStatus status;
	private CancellationReason cancellationReason;
	private BigDecimal totalAmount;
	private LocalDateTime createdAt;
	private List<OrderItemResponse> items;

}
