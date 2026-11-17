package com.subash.orderservice.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class OrderItemResponse {

	private Long id;
	private String productId;
	private String productName;
	private int quantity;
	private BigDecimal price;
}
