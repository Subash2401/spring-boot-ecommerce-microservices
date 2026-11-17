package com.subash.productservice.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Document(collection = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Product {
	@Id
	private String id;
	@NotBlank(message = "Name is required")
	private String name;
	private String description;
	@Positive(message = "Price must be positive")
	private BigDecimal price;
	@PositiveOrZero(message = "Quantity cannot be negative")
	private long quantity;
	@Version
	private Long version;
	// Stock held for orders (see InventoryService). Internal: never shown in the API,
	// because it contains other customers' order ids
	@JsonIgnore
	private List<StockHold> holds = new ArrayList<>();
}
