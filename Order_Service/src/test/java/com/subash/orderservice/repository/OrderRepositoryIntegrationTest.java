package com.subash.orderservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.subash.orderservice.model.Order;
import com.subash.orderservice.model.OrderStatus;
import com.subash.orderservice.model.OutboxEvent;
import com.subash.orderservice.model.OutboxStatus;

import jakarta.persistence.EntityManager;

// Integration test against a real MySQL 8.4 that Testcontainers starts in Docker for this
// test class and throws away afterwards (Docker must be running).
//
// @DataJpaTest starts only the database part of the app (JPA, repositories, Flyway), not
// Kafka, Config Server or the web layer. The database starts EMPTY, so Flyway really runs
// V1__initial_schema.sql, and ddl-auto=validate makes Hibernate check every entity
// against those tables: if V1 and the entities ever disagree, the test fails at startup.
// Each test runs in a transaction that is rolled back at the end.
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers
class OrderRepositoryIntegrationTest {

	// @ServiceConnection: Spring Boot points the DataSource at this container (URL, user, password)
	@Container
	@ServiceConnection
	static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	private OrderRepository orderRepository;
	@Autowired
	private EntityManager entityManager;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void flywayCreatesTheWholeSchema_onAnEmptyDatabase() {
		Integer v1 = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1", Integer.class);
		assertThat(v1).isEqualTo(1);
		// shedlock is not an entity, so Hibernate's validate does not check it: check it here
		Integer shedlockTables = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'shedlock'", Integer.class);
		assertThat(shedlockTables).isEqualTo(1);
	}

	@Test
	void findByUserId_returnsOnlyThatUsersOrders_newestFirst() {
		Order first = orderRepository.save(newOrder(1L));
		Order second = orderRepository.save(newOrder(1L));
		orderRepository.save(newOrder(2L)); // another user's order

		Page<Order> page = orderRepository.findByUserId(1L,
				PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

		assertThat(page.getContent()).extracting(Order::getId).containsExactly(second.getId(), first.getId());
	}

	// The 5-minute safety net: a PENDING order counts as stuck only once its event has
	// really reached Kafka (outbox row SENT) before the cutoff, not while it waits in the outbox.
	@Test
	void findStuck_findsPendingOrdersWhoseEventWasSentBeforeTheCutoff() {
		LocalDateTime now = LocalDateTime.now();
		Order sentLongAgo = orderRepository.save(newOrder(1L));
		Order stillInOutbox = orderRepository.save(newOrder(1L));
		saveOutboxRow(sentLongAgo, OutboxStatus.SENT, now.minusMinutes(10));
		saveOutboxRow(stillInOutbox, OutboxStatus.PENDING, null);

		List<Order> stuck = orderRepository.findStuck(OrderStatus.PENDING, now.minusMinutes(5), now.minusHours(24));

		assertThat(stuck).extracting(Order::getId).containsExactly(sentLongAgo.getId());
	}

	private static Order newOrder(Long userId) {
		Order order = new Order(); // @PrePersist sets status PENDING and createdAt
		order.setUserId(userId);
		order.setTotalAmount(new BigDecimal("10.00"));
		order.setItems(new ArrayList<>());
		return order;
	}

	private void saveOutboxRow(Order order, OutboxStatus status, LocalDateTime sentAt) {
		OutboxEvent row = new OutboxEvent("order-events", String.valueOf(order.getId()), "{}");
		row.setStatus(status);
		row.setSentAt(sentAt);
		entityManager.persist(row);
		entityManager.flush();
	}
}
