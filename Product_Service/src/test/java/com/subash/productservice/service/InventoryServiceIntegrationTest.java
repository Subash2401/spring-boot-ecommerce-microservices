package com.subash.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import com.subash.productservice.kafka.OrderEventItem;
import com.subash.productservice.model.Product;
import com.subash.productservice.model.Reservation;

// Stock holds against a real MongoDB 8.0 that Testcontainers starts in Docker (Docker must
// be running). Holds are atomic MongoDB updates ("take stock only if there is enough and this
// order holds none yet"), so only a real database can show that they work.
//
// @DataMongoTest starts only the MongoDB part of the app; @Import adds InventoryService.
@DataMongoTest
@Import(InventoryService.class)
@Testcontainers
class InventoryServiceIntegrationTest {

	@Container
	@ServiceConnection
	static MongoDBContainer mongo = new MongoDBContainer("mongo:8.0");

	@Autowired
	private InventoryService inventoryService;
	@Autowired
	private MongoTemplate mongoTemplate;

	@BeforeEach
	void emptyCollections() {
		mongoTemplate.dropCollection(Product.class);
		mongoTemplate.dropCollection(Reservation.class);
		mongoTemplate.insert(product("phone", 5));
		mongoTemplate.insert(product("cable", 1));
	}

	@Test
	void reserve_takesTheStock_andRemembersTheHold() {
		assertThat(inventoryService.reserve(1L, List.of(item("phone", 2)))).isTrue();

		Product phone = find("phone");
		assertThat(phone.getQuantity()).isEqualTo(3);
		assertThat(phone.getHolds()).hasSize(1);
		assertThat(phone.getHolds().get(0).getOrderId()).isEqualTo(1L);
	}

	@Test
	void reserve_sameOrderTwice_takesTheStockOnlyOnce() { // Kafka can deliver the same event again
		inventoryService.reserve(1L, List.of(item("phone", 2)));
		boolean second = inventoryService.reserve(1L, List.of(item("phone", 2)));

		assertThat(second).isTrue(); // still "reserved" ...
		assertThat(find("phone").getQuantity()).isEqualTo(3); // ... but 5 - 2, not 5 - 4
	}

	@Test
	void reserve_notEnoughOfOneItem_givesBackTheOtherHoldsToo() {
		// the phone can be held, the cable cannot (only 1 in stock)
		boolean reserved = inventoryService.reserve(1L, List.of(item("phone", 2), item("cable", 3)));

		assertThat(reserved).isFalse();
		assertThat(find("phone").getQuantity()).isEqualTo(5); // the phone hold was given back
		assertThat(find("phone").getHolds()).isEmpty();
		assertThat(find("cable").getQuantity()).isEqualTo(1);
	}

	@Test
	void releaseForOrder_givesTheStockBackExactlyOnce() { // FAILED, EXPIRED and CANCELLED can all arrive
		inventoryService.startOrResume(1L, List.of(item("phone", 2)));
		inventoryService.reserve(1L, List.of(item("phone", 2)));

		inventoryService.releaseForOrder(1L);
		inventoryService.releaseForOrder(1L);

		assertThat(find("phone").getQuantity()).isEqualTo(5); // 3 + 2, not 3 + 2 + 2
		assertThat(find("phone").getHolds()).isEmpty();
	}

	@Test
	void confirmForOrder_marksTheHoldPaid_andKeepsTheStockTaken() {
		inventoryService.reserve(1L, List.of(item("phone", 2)));

		inventoryService.confirmForOrder(1L);

		Product phone = find("phone");
		assertThat(phone.getQuantity()).isEqualTo(3);
		assertThat(phone.getHolds().get(0).isPaid()).isTrue();
	}

	private Product find(String id) {
		return mongoTemplate.findById(id, Product.class);
	}

	private static Product product(String id, long quantity) {
		return new Product(id, id, "test " + id, new BigDecimal("100.00"), quantity, null, new ArrayList<>());
	}

	private static OrderEventItem item(String productId, int quantity) {
		return new OrderEventItem(productId, quantity);
	}
}
