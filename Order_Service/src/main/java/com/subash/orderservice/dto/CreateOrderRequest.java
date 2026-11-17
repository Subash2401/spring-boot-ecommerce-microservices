package com.subash.orderservice.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateOrderRequest {
	@NotEmpty(message = "Order must have at least one item")
	@Size(max = 50, message = "Order cannot have more than 50 items")
	@Valid
	private List<@NotNull(message = "Item cannot be null") OrderItemRequest> items;
}
