package com.subash.paymentservice.model;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// A Kafka message saved in the same transaction as the payment change, then sent by
// OutboxPublisher. So the event is never lost and never sent for a change that rolled back.
@Entity
@Table(name = "outbox_events", indexes = @Index(name = "idx_outbox_status_id", columnList = "status, id"))
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String topic;

	@Column(name = "event_key", nullable = false, length = 100)
	private String eventKey;

	// Plain TEXT. No @Lob: on PostgreSQL that would store a large-object id instead of the text
	@Column(nullable = false, columnDefinition = "TEXT")
	private String payload;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private OutboxStatus status = OutboxStatus.PENDING;

	@Column(nullable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

	private LocalDateTime sentAt;

	public OutboxEvent(String topic, String eventKey, String payload) {
		this.topic = topic;
		this.eventKey = eventKey;
		this.payload = payload;
	}
}
