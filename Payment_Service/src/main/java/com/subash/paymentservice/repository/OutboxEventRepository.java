package com.subash.paymentservice.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.subash.paymentservice.model.OutboxEvent;
import com.subash.paymentservice.model.OutboxStatus;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

	List<OutboxEvent> findTop100ByStatusOrderByIdAsc(OutboxStatus status);

	@Modifying
	@Transactional
	@Query("DELETE FROM OutboxEvent e WHERE e.status = :status AND e.sentAt < :before")
	int deleteByStatusAndSentAtBefore(@Param("status") OutboxStatus status, @Param("before") LocalDateTime before);
}
