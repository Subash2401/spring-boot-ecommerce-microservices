package com.subash.paymentservice.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.subash.paymentservice.dto.InventoryEvent;
import com.subash.paymentservice.dto.OrderEvent;
import com.subash.paymentservice.dto.Paging;
import com.subash.paymentservice.dto.PaymentEvent;
import com.subash.paymentservice.dto.PaymentResponse;
import com.subash.paymentservice.exception.PaymentNotFoundException;
import com.subash.paymentservice.exception.PaymentNotPayableException;
import com.subash.paymentservice.gateway.PaymentGateway;
import com.subash.paymentservice.model.Payment;
import com.subash.paymentservice.model.PaymentStatus;
import com.subash.paymentservice.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

	private final PaymentRepository paymentRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final TransactionTemplate transactionTemplate;
	private final PaymentGateway paymentGateway;

	// Payment owns this deadline: pay() refuses after it, and PaymentExpiryJob expires the payment
	@Value("${payment.window-minutes:15}")
	private long paymentWindowMinutes;

	// No @Transactional here: save runs in its own transaction, so a duplicate order_id
	// can be caught. Inside an outer transaction it would be marked rollback-only and
	// fail later with UnexpectedRollbackException.
	public void createPendingPayment(InventoryEvent event) {
		if (paymentRepository.findByOrderId(event.getOrderId()).isPresent()) {
			log.info("Payment for order {} already exists, skipping", event.getOrderId());
			return;
		}
		Payment payment = Payment.builder().orderId(event.getOrderId()).userId(event.getUserId())
				.amount(event.getTotalAmount()).status(PaymentStatus.PENDING).build();
		try {
			paymentRepository.save(payment);
		} catch (DataIntegrityViolationException e) {
			// Another event saved a payment for this order at the same moment
			log.info("Payment for order {} already exists, skipping", event.getOrderId());
			return;
		}
		log.info("Pending payment created for orderId: {}", event.getOrderId());
	}

	// Three short steps instead of one transaction around the gateway call:
	// 1. claim: PENDING -> PROCESSING. @Version lets only one request win, so a double
	//    click can never charge twice; the loser gets 409.
	// 2. charge the gateway, with no database transaction open.
	// 3. record SUCCESS/FAILED and write the PaymentEvent to the outbox.
	// If the service dies between 2 and 3, PaymentRecoveryJob finishes step 3.
	public PaymentResponse pay(Long orderId, Long userId) {
		Payment claimed;
		try {
			claimed = transactionTemplate.execute(status -> claim(orderId, userId));
		} catch (OptimisticLockingFailureException e) {
			throw new PaymentNotPayableException(orderId, "it is already being processed");
		}

		boolean success = paymentGateway.charge(orderId, claimed.getAmount());

		try {
			return mapToResponse(completePayment(claimed.getId(), success));
		} catch (OptimisticLockingFailureException e) {
			// PaymentRecoveryJob resolved it at the same moment; return what it saved
			return mapToResponse(paymentRepository.findById(claimed.getId()).orElseThrow());
		}
	}

	private Payment claim(Long orderId, Long userId) {
		Payment payment = paymentRepository.findByOrderId(orderId).filter(p -> p.getUserId().equals(userId))
				.orElseThrow(() -> new PaymentNotFoundException("Payment not found for order: " + orderId));

		if (payment.getStatus() != PaymentStatus.PENDING) {
			throw new PaymentNotPayableException(orderId);
		}
		if (payment.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(paymentWindowMinutes))) {
			throw new PaymentNotPayableException(orderId, "the payment window has expired");
		}
		payment.setStatus(PaymentStatus.PROCESSING);
		// Flush now, so a lost race fails here instead of at commit
		return paymentRepository.saveAndFlush(payment);
	}

	// Step 3, also called by PaymentRecoveryJob. Only a PROCESSING payment is changed.
	public Payment completePayment(Long paymentId, boolean success) {
		return transactionTemplate.execute(status -> {
			Payment payment = paymentRepository.findById(paymentId).orElseThrow();
			if (payment.getStatus() != PaymentStatus.PROCESSING) {
				log.info("Payment {} is already {}, not changing it", paymentId, payment.getStatus());
				return payment;
			}
			if (success && payment.isOrderCancelled()) {
				// The order was cancelled while the gateway was charging: refund at once (mock refund)
				payment.setStatus(PaymentStatus.REFUNDED);
				log.warn("Payment for cancelled order {} succeeded; refunded", payment.getOrderId());
			} else {
				payment.setStatus(success ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
			}
			payment.setTransactionId(success ? UUID.randomUUID().toString() : null);
			paymentRepository.save(payment);
			log.info("Payment {} for orderId: {}", payment.getStatus(), payment.getOrderId());
			publish(payment);
			return payment;
		});
	}

	// Payment owns the payment deadline. Called by PaymentExpiryJob for a PENDING payment
	// older than the window. Order cancels the order when the EXPIRED event arrives.
	public void expirePayment(Long paymentId) {
		transactionTemplate.executeWithoutResult(status -> {
			Payment payment = paymentRepository.findById(paymentId).orElseThrow();
			if (payment.getStatus() != PaymentStatus.PENDING) {
				return;
			}
			payment.setStatus(PaymentStatus.EXPIRED);
			paymentRepository.save(payment);
			log.info("Payment for order {} expired", payment.getOrderId());
			publish(payment);
		});
	}

	// Saved to the outbox in the caller's transaction (PaymentEventOutboxWriter), sent by OutboxPublisher
	private void publish(Payment payment) {
		eventPublisher.publishEvent(PaymentEvent.builder().orderId(payment.getOrderId()).userId(payment.getUserId())
				.amount(payment.getAmount()).status(payment.getStatus().name())
				.transactionId(payment.getTransactionId()).build());
	}

	// No @Transactional, for the same reason as createPendingPayment
	public void handleCancellation(OrderEvent event) {
		Optional<Payment> existing = paymentRepository.findByOrderId(event.getOrderId());

		// Cancelled before any payment existed: save a CANCELLED payment, so a late RESERVED
		// event finds it and createPendingPayment skips this order
		if (existing.isEmpty()) {
			Payment payment = Payment.builder().orderId(event.getOrderId()).userId(event.getUserId())
					.amount(event.getTotalAmount()).status(PaymentStatus.CANCELLED).build();
			try {
				paymentRepository.save(payment);
				log.info("Order {} cancelled before payment was created, saved CANCELLED payment", event.getOrderId());
				return;
			} catch (DataIntegrityViolationException e) {
				// A RESERVED event saved a payment at the same moment: re-read it and cancel it below
				log.info("Payment for order {} already exists, re-reading it", event.getOrderId());
				existing = paymentRepository.findByOrderId(event.getOrderId());
				if (existing.isEmpty()) {
					return;
				}
			}
		}

		Long paymentId = existing.get().getId();
		transactionTemplate.executeWithoutResult(status -> {
			Payment payment = paymentRepository.findById(paymentId).orElseThrow();
			switch (payment.getStatus()) {
			case PENDING -> {
				payment.setStatus(PaymentStatus.FAILED);
				log.info("Pending payment cancelled for orderId: {}", event.getOrderId());
			}
			case PROCESSING -> {
				// The gateway is charging right now; completePayment refunds it if it succeeds
				payment.setOrderCancelled(true);
				log.info("Order {} cancelled while its payment is processing", event.getOrderId());
			}
			case SUCCESS -> {
				// Safety net: an already-paid order was cancelled, so refund it (mock refund).
				// Users get 409 when they cancel a CONFIRMED order, so this happens only when the
				// cancel came before Order learned about the payment.
				payment.setStatus(PaymentStatus.REFUNDED);
				log.warn("Order {} was cancelled after payment succeeded; refunded", event.getOrderId());
				publish(payment);
			}
			default -> {
				log.info("Payment for order {} is {}, nothing to cancel", event.getOrderId(), payment.getStatus());
				return;
			}
			}
			// @Version: if pay() changed the payment at the same moment, this fails and Kafka retries
			paymentRepository.save(payment);
		});
	}

	// Someone else's payment gives the same 404 as a missing one, so its existence
	// is not revealed
	public PaymentResponse getPaymentById(Long id, Long userId, boolean isAdmin) {
		Payment payment = paymentRepository.findById(id).filter(p -> isAdmin || p.getUserId().equals(userId))
				.orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + id));
		return mapToResponse(payment);
	}

	public PaymentResponse getPaymentByOrderId(Long orderId, Long userId, boolean isAdmin) {
		Payment payment = paymentRepository.findByOrderId(orderId).filter(p -> isAdmin || p.getUserId().equals(userId))
				.orElseThrow(() -> new PaymentNotFoundException("Payment not found for order: " + orderId));
		return mapToResponse(payment);
	}

	// Newest first; id breaks ties so the order is stable across pages
	public Page<PaymentResponse> getPaymentsByUser(Long userId, Pageable pageable) {
		Pageable page = Paging.of(pageable, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
		return paymentRepository.findByUserId(userId, page).map(this::mapToResponse);
	}

	private PaymentResponse mapToResponse(Payment payment) {
		return PaymentResponse.builder().id(payment.getId()).userId(payment.getUserId()).orderId(payment.getOrderId())
				.amount(payment.getAmount()).status(payment.getStatus().name())
				.transactionId(payment.getTransactionId()).createdAt(payment.getCreatedAt())
				.expiresAt(payment.getCreatedAt() == null ? null
						: payment.getCreatedAt().plusMinutes(paymentWindowMinutes).toInstant(ZoneOffset.UTC))
				.build();
	}

}
