package com.subash.orderservice.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.subash.orderservice.dto.CreateOrderRequest;
import com.subash.orderservice.dto.OrderResponse;
import com.subash.orderservice.service.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
// Every endpoint needs a logged-in user (second layer behind the URL rules); ownership is checked in the service
@PreAuthorize("isAuthenticated()")
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

	private final OrderService orderService;

	@PostMapping
	public ResponseEntity<OrderResponse> placeOrder(
			@Valid @RequestBody CreateOrderRequest request, @AuthenticationPrincipal Long userId){
		return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(request, userId));
	}

	// ?page=0&size=20 (size at most 50); newest first
	@GetMapping("/me")
	public ResponseEntity<PagedModel<OrderResponse>> getMyOrders(@AuthenticationPrincipal Long userId,
			@PageableDefault(size = 20) Pageable pageable){
		return ResponseEntity.ok(new PagedModel<>(orderService.getOrdersByUser(userId, pageable)));
	}

	@GetMapping("/{id}")
	public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id,
			@AuthenticationPrincipal Long userId, Authentication authentication){
		return ResponseEntity.ok(orderService.getOrderById(id, userId, isAdmin(authentication)));
	}

	@PutMapping("/{id}/cancel")
	public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long id,
			@AuthenticationPrincipal Long userId, Authentication authentication){
		return ResponseEntity.ok(orderService.cancelOrder(id, userId, isAdmin(authentication)));
	}

	private boolean isAdmin(Authentication authentication) {
		return authentication.getAuthorities().stream()
				.anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
	}

}
