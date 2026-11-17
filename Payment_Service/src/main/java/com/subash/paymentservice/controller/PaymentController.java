package com.subash.paymentservice.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.subash.paymentservice.dto.PaymentResponse;
import com.subash.paymentservice.service.PaymentService;

import lombok.RequiredArgsConstructor;

@RestController
// Every endpoint needs a logged-in user (second layer behind the URL rules); ownership is checked in the service
@PreAuthorize("isAuthenticated()")
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentService paymentService;

	// ?page=0&size=20 (size at most 50); newest first
	@GetMapping("/me")
	public ResponseEntity<PagedModel<PaymentResponse>> getMyPayments(@AuthenticationPrincipal Long userId,
			@PageableDefault(size = 20) Pageable pageable) {
		return ResponseEntity.ok(new PagedModel<>(paymentService.getPaymentsByUser(userId, pageable)));
	}

	@GetMapping("/{id}")
	public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable Long id, @AuthenticationPrincipal Long userId,
			Authentication authentication) {
		return ResponseEntity.ok(paymentService.getPaymentById(id, userId, isAdmin(authentication)));
	}

	@GetMapping("/order/{orderId}")
	public ResponseEntity<PaymentResponse> getPaymentByOrderId(@PathVariable Long orderId,
			@AuthenticationPrincipal Long userId, Authentication authentication) {
		return ResponseEntity.ok(paymentService.getPaymentByOrderId(orderId, userId, isAdmin(authentication)));
	}

	@PostMapping("/order/{orderId}/pay")
	public ResponseEntity<PaymentResponse> pay(@PathVariable Long orderId, @AuthenticationPrincipal Long userId) {
		return ResponseEntity.ok(paymentService.pay(orderId, userId));
	}

	private boolean isAdmin(Authentication authentication) {
		return authentication.getAuthorities().stream()
				.anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
	}
}
