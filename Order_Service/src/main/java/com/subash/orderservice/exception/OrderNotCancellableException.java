package com.subash.orderservice.exception;

import com.subash.orderservice.model.OrderStatus;

public class OrderNotCancellableException extends RuntimeException {

	public OrderNotCancellableException(Long id, OrderStatus status) {
		super("Order " + id + " cannot be cancelled because it is already " + status);
	}
}
