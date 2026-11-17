package com.subash.orderservice.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.subash.orderservice.model.Order;
import com.subash.orderservice.model.OrderStatus;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

	Page<Order> findByUserId(Long userId, Pageable pageable);

	List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime time);

	// PENDING orders whose OrderEvent reached Kafka before sentBefore (the outbox row is SENT),
	// or that are older than createdBefore (hard limit, also covers rows the monthly cleanup deleted)
	@Query("""
			SELECT o FROM Order o WHERE o.status = :status AND (o.createdAt < :createdBefore OR EXISTS (
			  SELECT e FROM OutboxEvent e WHERE e.eventKey = CAST(o.id AS String)
			  AND e.status = com.subash.orderservice.model.OutboxStatus.SENT AND e.sentAt < :sentBefore))
			""")
	List<Order> findStuck(@Param("status") OrderStatus status, @Param("sentBefore") LocalDateTime sentBefore,
			@Param("createdBefore") LocalDateTime createdBefore);
}
