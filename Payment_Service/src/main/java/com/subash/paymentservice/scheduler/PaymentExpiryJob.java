package com.subash.paymentservice.scheduler;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.subash.paymentservice.model.PaymentStatus;
import com.subash.paymentservice.repository.PaymentRepository;
import com.subash.paymentservice.service.PaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

// Payment Service owns the payment deadline. A PENDING payment older than the window
// becomes EXPIRED and an EXPIRED event goes out through the outbox; Order then cancels
// the order (PAYMENT_TIMEOUT). Because the same service both accepts payments and expires
// them, a payment can never be charged after it expired (the @Version claim decides).
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentExpiryJob {

	private final PaymentRepository paymentRepository;
	private final PaymentService paymentService;

	@Value("${payment.window-minutes:15}")
	private long paymentWindowMinutes;

	@Scheduled(fixedDelay = 60000)
	@SchedulerLock(name = "paymentExpiryJob", lockAtMostFor = "PT50S")
	public void expireUnpaidPayments() {
		LocalDateTime cutoff = LocalDateTime.now().minusMinutes(paymentWindowMinutes);
		paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, cutoff).forEach(payment -> {
			try {
				paymentService.expirePayment(payment.getId());
			} catch (Exception e) {
				log.error("Could not expire payment {}, will retry next run", payment.getId(), e);
			}
		});
	}
}
