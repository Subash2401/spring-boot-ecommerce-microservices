package com.subash.orderservice.exception;

// The order names a product that Product Service does not know -> 400
public class ProductNotFoundException extends RuntimeException {

	public ProductNotFoundException(String productId) {
		super("Product " + productId + " not found");
	}
}
