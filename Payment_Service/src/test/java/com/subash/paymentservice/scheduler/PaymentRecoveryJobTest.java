package com.subash.paymentservice.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.subash.paymentservice.gateway.PaymentGateway;
import com.subash.paymentservice.model.Payment;
import com.subash.paymentservice.model.PaymentStatus;
import com.subash.paymentservice.repository.PaymentRepository;
import com.subash.paymentservice.service.PaymentService;

// The recovery job finishes payments that were left in PROCESSING (the service died
// between charging the gateway and saving the result).
@ExtendWith(MockitoExtension.class)
class PaymentRecoveryJobTest {

	@Mock
	private PaymentRepository paymentRepository;
	@Mock
	private PaymentGateway paymentGateway;
	@Mock
	private PaymentService paymentService;

	@InjectMocks
	private PaymentRecoveryJob job;

	@Test
	void stuckPayments_areCompletedWithWhatTheGatewaySays_andOneFailureDoesNotStopTheOthers() {
		Payment charged = Payment.builder().id(1L).orderId(100L).status(PaymentStatus.PROCESSING).build();
		Payment neverCharged = Payment.builder().id(2L).orderId(200L).status(PaymentStatus.PROCESSING).build();
		when(paymentRepository.findByStatusAndUpdatedAtBefore(eq(PaymentStatus.PROCESSING), any()))
				.thenReturn(List.of(charged, neverCharged));
		when(paymentGateway.findResult(100L)).thenReturn(Optional.of(true));
		when(paymentGateway.findResult(200L)).thenReturn(Optional.empty()); // the gateway never saw it
		// the first one fails to save (say, the database is busy) ...
		when(paymentService.completePayment(1L, true)).thenThrow(new RuntimeException("database busy"));

		job.resolveStuckPayments();

		// ... the second one is still completed, as FAILED because it was never charged;
		// the first one is simply tried again on the next run
		verify(paymentService).completePayment(1L, true);
		verify(paymentService).completePayment(2L, false);
	}
}
