package com.subash.productservice.service;

import java.time.LocalDateTime;
import java.util.List;

import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.subash.productservice.kafka.OrderEventItem;
import com.subash.productservice.model.Product;
import com.subash.productservice.model.Reservation;
import com.subash.productservice.model.StockHold;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Stock for an order is tracked as a hold {orderId, quantity} inside each product document.
// Taking stock = "quantity -= q and add the hold", giving it back = "quantity += q and remove
// the hold". Each is ONE atomic update on ONE document, with a condition that makes it run
// at most once per order. So every step can be retried after a crash or a redelivered Kafka
// message without taking or returning stock twice, and no Mongo transactions are needed.
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

	private final MongoTemplate mongoTemplate;

	// The decision record for an order: the existing one, or a new RESERVING one
	public Reservation startOrResume(Long orderId, List<OrderEventItem> items) {
		Reservation existing = findReservation(orderId);
		if (existing != null) {
			return existing;
		}
		try {
			return mongoTemplate.insert(new Reservation(null, orderId, items, "RESERVING", true));
		} catch (DuplicateKeyException e) {
			return findReservation(orderId);
		}
	}

	// Holds stock for every item. If one item is short, the holds already taken for this
	// order are given back and the result is false. Safe to call again for the same order.
	public boolean reserve(Long orderId, List<OrderEventItem> items) {
		for (OrderEventItem item : items) {
			if (!holdStock(orderId, item)) {
				log.warn("Not enough stock for product {} (order {})", item.getProductId(), orderId);
				releaseHolds(orderId);
				return false;
			}
		}
		return true;
	}

	private boolean holdStock(Long orderId, OrderEventItem item) {
		Query enoughStockAndNoHoldYet = Query.query(Criteria.where("id").is(item.getProductId())
				.and("quantity").gte(item.getQuantity()).and("holds.orderId").ne(orderId));
		Update takeStock = new Update().inc("quantity", -item.getQuantity())
				.push("holds", new StockHold(orderId, item.getQuantity(), false, LocalDateTime.now()))
				.inc("version", 1);
		if (mongoTemplate.updateFirst(enoughStockAndNoHoldYet, takeStock, Product.class).getModifiedCount() == 1) {
			return true;
		}
		// Not changed: either this order already holds the stock (a retry), or there is not enough
		return mongoTemplate.exists(
				Query.query(Criteria.where("id").is(item.getProductId()).and("holds.orderId").is(orderId)),
				Product.class);
	}

	public void markReservation(Long orderId, String status) {
		mongoTemplate.updateFirst(Query.query(Criteria.where("orderId").is(orderId)),
				new Update().set("status", status), Reservation.class);
	}

	// Payment succeeded: the held stock is now sold. The holds stay (marked paid) so a refund
	// can still give the stock back; HoldCleanupJob removes them after 7 days.
	public void confirmForOrder(Long orderId) {
		mongoTemplate.updateMulti(Query.query(Criteria.where("holds.orderId").is(orderId)),
				new Update().set("holds.$[hold].paid", true).filterArray(Criteria.where("hold.orderId").is(orderId)),
				Product.class);
		mongoTemplate.updateFirst(Query.query(Criteria.where("orderId").is(orderId).and("status").is("RESERVED")),
				new Update().set("status", "CONFIRMED"), Reservation.class);
	}

	// Order cancelled, payment failed or refunded: give the stock back exactly once
	public void releaseForOrder(Long orderId) {
		Reservation reservation = findReservation(orderId);
		if (reservation != null && !reservation.isUsesHolds()) {
			releaseOldReservation(orderId);
			return;
		}
		int released = releaseHolds(orderId);
		markReservation(orderId, "RELEASED");
		if (released > 0) {
			log.info("Released held stock for order {} ({} products)", orderId, released);
		} else {
			log.info("No held stock for order {}, nothing to release", orderId);
		}
	}

	// For each product holding stock for this order: remove the hold and add its quantity back.
	// The "hold still there" condition makes a second call a no-op.
	private int releaseHolds(Long orderId) {
		int released = 0;
		List<Product> products = mongoTemplate.find(Query.query(Criteria.where("holds.orderId").is(orderId)),
				Product.class);
		for (Product product : products) {
			for (StockHold hold : product.getHolds()) {
				if (!orderId.equals(hold.getOrderId())) {
					continue;
				}
				Query stillHeld = Query.query(Criteria.where("id").is(product.getId()).and("holds.orderId").is(orderId));
				Update giveBack = new Update().inc("quantity", hold.getQuantity())
						.pull("holds", new Document("orderId", orderId)).inc("version", 1);
				released += (int) mongoTemplate.updateFirst(stillHeld, giveBack, Product.class).getModifiedCount();
			}
		}
		return released;
	}

	// Reservations made before holds existed have no holds; give their stock back from items.
	// Simplification: a crash between the two steps here can lose this release (old behaviour);
	// only orders reserved before this change can take this path.
	private void releaseOldReservation(Long orderId) {
		Query active = Query.query(Criteria.where("orderId").is(orderId).and("status").in("RESERVED", "CONFIRMED"));
		Reservation reservation = mongoTemplate.findAndModify(active, new Update().set("status", "RELEASED"),
				FindAndModifyOptions.options().returnNew(false), Reservation.class);
		if (reservation == null) {
			log.info("No active reservation for order {}, nothing to release", orderId);
			return;
		}
		for (OrderEventItem item : reservation.getItems()) {
			mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(item.getProductId())),
					new Update().inc("quantity", item.getQuantity()).inc("version", 1), Product.class);
		}
		log.info("Released stock for old reservation of order {}", orderId);
	}

	private Reservation findReservation(Long orderId) {
		return mongoTemplate.findOne(Query.query(Criteria.where("orderId").is(orderId)), Reservation.class);
	}
}
