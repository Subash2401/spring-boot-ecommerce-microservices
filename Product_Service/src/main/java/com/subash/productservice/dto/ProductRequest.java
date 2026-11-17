package com.subash.productservice.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Body of POST and PUT /products. Only the fields an admin may set:
// id and version are never taken from the request.
@Getter
@Setter
@NoArgsConstructor
public class ProductRequest {

	@NotBlank(message = "Name is required")
	@Size(max = 100, message = "Name cannot be more than 100 characters")
	private String name;

	@Size(max = 1000, message = "Description cannot be more than 1000 characters")
	private String description;

	@NotNull(message = "Price is required")
	@Positive(message = "Price must be positive")
	private BigDecimal price;

	@NotNull(message = "Quantity is required")
	@PositiveOrZero(message = "Quantity cannot be negative")
	private Long quantity;
}
