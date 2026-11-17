package com.subash.paymentservice.scheduler;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.subash.paymentservice.gateway.PaymentGateway;
import com.subash.paymentservice.model.PaymentStatus;
import com.subash.paymentservice.repository.PaymentRepository;
import com.subash.paymentservice.service.PaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

// A payment stays PROCESSING only if the service died (or the gateway call failed)
// between charging and saving the result. After 2 minutes this job asks the gateway what
// happened for that order and finishes the payment, which also writes the PaymentEvent
// to the outbox. If the gateway has no record, nothing was charged, so it becomes FAILED.
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRecoveryJob {

	private static final long STUCK_AFTER_MINUTES = 2;

	private final PaymentRepository paymentRepository;
	private final PaymentGateway paymentGateway;
	private final PaymentService paymentService;

	@Scheduled(fixedDelay = 60000)
	@SchedulerLock(name = "paymentRecoveryJob", lockAtMostFor = "PT50S")
	public void resolveStuckPayments() {
		LocalDateTime cutoff = LocalDateTime.now().minusMinutes(STUCK_AFTER_MINUTES);
		paymentRepository.findByStatusAndUpdatedAtBefore(PaymentStatus.PROCESSING, cutoff).forEach(payment -> {
			boolean charged = paymentGateway.findResult(payment.getOrderId()).orElse(false);
			log.warn("Payment {} (order {}) stuck in PROCESSING, gateway says charged={}", payment.getId(),
					payment.getOrderId(), charged);
			try {
				paymentService.completePayment(payment.getId(), charged);
			} catch (Exception e) {
				log.error("Could not resolve stuck payment {}, will retry next run", payment.getId(), e);
			}
		});
	}
}
