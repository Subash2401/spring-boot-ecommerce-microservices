package com.subash.paymentservice.gateway;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

// Simulated gateway: 80% of charges succeed. It remembers each result by orderId, like a
// real gateway's "look up by idempotency key". Simplification: the results live in memory,
// so after a restart findResult() knows nothing and a stuck payment is resolved as FAILED.
@Component
public class MockPaymentGateway implements PaymentGateway {

	private final Map<Long, Boolean> results = new ConcurrentHashMap<>();

	@Override
	public boolean charge(Long orderId, BigDecimal amount) {
		boolean success = Math.random() > 0.2;
		results.put(orderId, success);
		return success;
	}

	@Override
	public Optional<Boolean> findResult(Long orderId) {
		return Optional.ofNullable(results.get(orderId));
	}
}
