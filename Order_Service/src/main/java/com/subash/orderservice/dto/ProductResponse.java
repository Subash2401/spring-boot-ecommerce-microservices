package com.subash.orderservice.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class ProductResponse {
	
	private String id;
	private String name;
	private String description;
	private BigDecimal price;
	private int quantity;
}
