package com.subash.orderservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class OrderItemRequest {

	@NotBlank(message = "Product Id is required")
	private String productId;

	// Integer, not int: a missing quantity stays null and fails @NotNull instead of becoming 0
	@NotNull(message = "Quantity is required")
	@Positive(message = "Quantity must be at least 1")
	@Max(value = 100, message = "Quantity cannot be more than 100")
	private Integer quantity;
}
