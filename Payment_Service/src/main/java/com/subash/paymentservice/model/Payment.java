package com.subash.paymentservice.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payments", indexes = @Index(name = "idx_payments_user_created", columnList = "userId, createdAt"))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Payment {

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	private Long id;
	
	@Column(unique = true)
	private Long orderId;
	private Long userId;
	private BigDecimal amount;
	
	@Enumerated(EnumType.STRING)
	// Plain VARCHAR, so Hibernate does not add a CHECK constraint that blocks new statuses
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 20)
	private PaymentStatus status;
	
	private String transactionId;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	// Optimistic lock: two requests that change the same payment at once cannot both win.
	// "default 0" lets PostgreSQL add the column to existing rows.
	@Version
	@Column(nullable = false, columnDefinition = "bigint default 0")
	private Long version;

	// Set when the order's CANCELLED event arrives while the payment is PROCESSING, so a
	// charge that succeeds afterwards is refunded at once
	@Builder.Default
	@Column(nullable = false, columnDefinition = "boolean default false")
	private boolean orderCancelled = false;
	
	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
		updatedAt = LocalDateTime.now();
	}
	
	@PreUpdate
	protected void onUpdate() {
		updatedAt = LocalDateTime.now();
	}
	
	
}
