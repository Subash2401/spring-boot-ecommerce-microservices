package com.subash.paymentservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.subash.paymentservice.model.Payment;
import com.subash.paymentservice.model.PaymentStatus;

// Integration test against a real PostgreSQL 17 that Testcontainers starts in Docker
// (Docker must be running). The database starts EMPTY: Flyway runs V1__initial_schema.sql
// and ddl-auto=validate makes Hibernate check every entity against those tables.
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers
class PaymentRepositoryIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

	@Autowired
	private PaymentRepository paymentRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void flywayCreatesTheWholeSchema_onAnEmptyDatabase() {
		Integer v1 = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class);
		assertThat(v1).isEqualTo(1);
		Integer shedlockTables = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'shedlock'", Integer.class);
		assertThat(shedlockTables).isEqualTo(1);
	}

	// One payment per order, enforced by the database. createPendingPayment relies on this
	// when two RESERVED events for the same order arrive at the same moment.
	@Test
	void aSecondPaymentForTheSameOrder_isRejectedByTheDatabase() {
		paymentRepository.saveAndFlush(newPayment(42L));

		assertThatThrownBy(() -> paymentRepository.saveAndFlush(newPayment(42L)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void newPayment_startsAtVersion0_andGetsItsTimestamps() {
		Payment saved = paymentRepository.saveAndFlush(newPayment(43L));

		assertThat(saved.getVersion()).isZero(); // @Version, used for the claim step in pay()
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.isOrderCancelled()).isFalse();
	}

	private static Payment newPayment(Long orderId) {
		return Payment.builder().orderId(orderId).userId(7L).amount(new BigDecimal("999.00"))
				.status(PaymentStatus.PENDING).build();
	}
}
