package com.subash.orderservice.scheduler;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.subash.orderservice.model.OrderStatus;
import com.subash.orderservice.repository.OrderRepository;
import com.subash.orderservice.service.OrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentTimeoutScheduler {

	private final OrderRepository orderRepository;
	private final OrderService orderService;

	// Safety net only: Payment Service owns the payment deadline (PaymentExpiryJob, EXPIRED event).
	// This catches orders whose EXPIRED event never arrived.
	@Value("${order.payment-timeout-minutes:60}")
	private long timeoutMinutes;

	@Value("${order.pending-timeout-minutes:5}")
	private long pendingTimeoutMinutes;

	// A PENDING order is cancelled after this many hours even if its event never left the outbox
	private static final long PENDING_HARD_LIMIT_HOURS = 24;

	@Scheduled(fixedDelay = 60000)
	@SchedulerLock(name = "paymentTimeoutJob", lockAtMostFor = "PT50S")
	public void cancelUnpaidOrders() {
		LocalDateTime cutoff = LocalDateTime.now().minusMinutes(timeoutMinutes);
		orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.AWAITING_PAYMENT, cutoff).forEach(order -> {
			try {
				orderService.expireUnpaidOrder(order.getId());
			} catch (Exception e) {
				log.error("Could not expire unpaid order {}, will retry next run", order.getId(), e);
			}
		});
	}

	@Scheduled(fixedDelay = 60000)
	@SchedulerLock(name = "pendingTimeoutJob", lockAtMostFor = "PT50S")
	public void cancelStuckOrders() {
		// The 5 minutes start when the OrderEvent actually reached Kafka (outbox row SENT), not when
		// the order was created. While Kafka is down the event waits in the outbox, and the
		// order waits too instead of being cancelled for nothing.
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime sentBefore = now.minusMinutes(pendingTimeoutMinutes);
		LocalDateTime createdBefore = now.minusHours(PENDING_HARD_LIMIT_HOURS);
		orderRepository.findStuck(OrderStatus.PENDING, sentBefore, createdBefore).forEach(order -> {
			try {
				orderService.expireStuckOrder(order.getId());
			} catch (Exception e) {
				log.error("Could not expire stuck order {}, will retry next run", order.getId(), e);
			}
		});
	}
}