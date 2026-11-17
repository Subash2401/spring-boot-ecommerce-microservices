package com.subash.paymentservice.gateway;

import java.math.BigDecimal;
import java.util.Optional;

public interface PaymentGateway {

	boolean charge(Long orderId, BigDecimal amount);

	// What the gateway recorded for this order: true = charged, false = declined,
	// empty = it never received a charge. Used to resolve payments stuck in PROCESSING.
	Optional<Boolean> findResult(Long orderId);
}
