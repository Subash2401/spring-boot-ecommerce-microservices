package com.subash.paymentservice.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.subash.paymentservice.model.Payment;
import com.subash.paymentservice.model.PaymentStatus;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
	
	Optional<Payment> findByOrderId(Long orderId);
	
	Page<Payment> findByUserId(Long userId, Pageable pageable);

	List<Payment> findByStatusAndUpdatedAtBefore(PaymentStatus status, LocalDateTime time);

	List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime time);

}

