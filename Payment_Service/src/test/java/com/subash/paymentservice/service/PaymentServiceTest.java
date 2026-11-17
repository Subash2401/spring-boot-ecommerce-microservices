package com.subash.paymentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.subash.paymentservice.dto.OrderEvent;
import com.subash.paymentservice.dto.PaymentEvent;
import com.subash.paymentservice.dto.PaymentResponse;
import com.subash.paymentservice.exception.PaymentNotFoundException;
import com.subash.paymentservice.exception.PaymentNotPayableException;
import com.subash.paymentservice.gateway.PaymentGateway;
import com.subash.paymentservice.model.Payment;
import com.subash.paymentservice.model.PaymentStatus;
import com.subash.paymentservice.repository.PaymentRepository;

// Unit tests for the payment "claim, charge, complete" steps. No Spring, no database:
// Mockito fakes the repository, the payment gateway and the event publisher.
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

	private static final Long ORDER_ID = 10L;
	private static final Long USER_ID = 7L;

	@Mock
	private PaymentRepository paymentRepository;
	@Mock
	private ApplicationEventPublisher eventPublisher;
	@Mock
	private PaymentGateway paymentGateway;
	@Mock
	private PlatformTransactionManager transactionManager;

	private PaymentService paymentService;

	@BeforeEach
	void setUp() {
		// A real TransactionTemplate on a fake transaction manager: the code inside
		// execute(...) runs normally, there is just no real database transaction around it.
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		paymentService = new PaymentService(paymentRepository, eventPublisher, transactionTemplate, paymentGateway);
		// The @Value field that Spring would fill from payment.window-minutes
		ReflectionTestUtils.setField(paymentService, "paymentWindowMinutes", 15L);
	}

	// ---- pay: claim -> charge -> complete -----------------------------------------------

	@Test
	void pay_pendingPayment_isChargedOnce_andSucceeds() {
		Payment payment = payment(PaymentStatus.PENDING, LocalDateTime.now());
		when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
		when(paymentRepository.saveAndFlush(payment)).thenReturn(payment);
		when(paymentGateway.charge(ORDER_ID, payment.getAmount())).thenReturn(true);
		when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

		PaymentResponse response = paymentService.pay(ORDER_ID, USER_ID);

		assertThat(response.getStatus()).isEqualTo("SUCCESS");
		assertThat(response.getTransactionId()).isNotBlank();
		verify(paymentGateway, times(1)).charge(ORDER_ID, payment.getAmount());
		// the SUCCESS event goes to the outbox, then to Order (CONFIRMED) and Notification
		assertThat(publishedEvent().getStatus()).isEqualTo("SUCCESS");
	}

	@Test
	void pay_whenAlreadyProcessing_isRefused_andNeverChargedTwice() { // a double click -> 409
		Payment payment = payment(PaymentStatus.PROCESSING, LocalDateTime.now());
		when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));

		assertThatThrownBy(() -> paymentService.pay(ORDER_ID, USER_ID)).isInstanceOf(PaymentNotPayableException.class);
		verifyNoInteractions(paymentGateway);
	}

	@Test
	void pay_twoRequestsAtOnce_theLoserIsRefused() {
		// Both requests read PENDING; the @Version check lets only the first save win.
		// The second save fails with an optimistic locking error -> 409, no charge.
		Payment payment = payment(PaymentStatus.PENDING, LocalDateTime.now());
		when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
		when(paymentRepository.saveAndFlush(payment))
				.thenThrow(new ObjectOptimisticLockingFailureException(Payment.class, payment.getId()));

		assertThatThrownBy(() -> paymentService.pay(ORDER_ID, USER_ID)).isInstanceOf(PaymentNotPayableException.class)
				.hasMessageContaining("already being processed");
		verifyNoInteractions(paymentGateway);
	}

	@Test
	void pay_afterThe15MinuteWindow_isRefused() {
		Payment payment = payment(PaymentStatus.PENDING, LocalDateTime.now().minusMinutes(16));
		when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));

		assertThatThrownBy(() -> paymentService.pay(ORDER_ID, USER_ID)).isInstanceOf(PaymentNotPayableException.class)
				.hasMessageContaining("expired");
		verifyNoInteractions(paymentGateway);
	}

	@Test
	void pay_someoneElsesPayment_looksLikeAMissingPayment() { // 404
		Payment payment = payment(PaymentStatus.PENDING, LocalDateTime.now());
		payment.setUserId(99L);
		when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));

		assertThatThrownBy(() -> paymentService.pay(ORDER_ID, USER_ID)).isInstanceOf(PaymentNotFoundException.class);
		verifyNoInteractions(paymentGateway);
	}

	// ---- completePayment (also used by PaymentRecoveryJob) ------------------------------

	@Test
	void completePayment_chargedButOrderCancelledMeanwhile_isRefunded() {
		Payment payment = payment(PaymentStatus.PROCESSING, LocalDateTime.now());
		payment.setOrderCancelled(true); // the CANCELLED event arrived while the gateway was charging
		when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

		Payment result = paymentService.completePayment(payment.getId(), true);

		assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
		assertThat(publishedEvent().getStatus()).isEqualTo("REFUNDED");
	}

	@Test
	void completePayment_alreadyResolved_isLeftAlone() { // e.g. the recovery job got there first
		Payment payment = payment(PaymentStatus.SUCCESS, LocalDateTime.now());
		when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

		Payment result = paymentService.completePayment(payment.getId(), false);

		assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
		verify(paymentRepository, never()).save(any());
		verifyNoInteractions(eventPublisher);
	}

	// ---- handleCancellation (order cancelled, from Kafka) -------------------------------

	@Test
	void handleCancellation_ofAnAlreadyPaidOrder_refundsThePayment() { // the safety net
		Payment payment = payment(PaymentStatus.SUCCESS, LocalDateTime.now());
		when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
		when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

		paymentService.handleCancellation(new OrderEvent(ORDER_ID, USER_ID, payment.getAmount(), "CANCELLED"));

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
		verify(paymentRepository).save(payment);
		assertThat(publishedEvent().getStatus()).isEqualTo("REFUNDED");
	}

	// ---- helpers -------------------------------------------------------------------------

	private static Payment payment(PaymentStatus status, LocalDateTime createdAt) {
		Payment payment = Payment.builder().id(5L).orderId(ORDER_ID).userId(USER_ID)
				.amount(new BigDecimal("999.00")).status(status).build();
		payment.setCreatedAt(createdAt);
		return payment;
	}

	// The PaymentEvent that PaymentService handed to the event publisher (-> outbox)
	private PaymentEvent publishedEvent() {
		ArgumentCaptor<PaymentEvent> captor = ArgumentCaptor.forClass(PaymentEvent.class);
		verify(eventPublisher).publishEvent(captor.capture());
		return captor.getValue();
	}
}
