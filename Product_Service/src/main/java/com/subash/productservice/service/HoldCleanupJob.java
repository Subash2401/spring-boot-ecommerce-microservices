package com.subash.productservice.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mongodb.client.result.UpdateResult;
import com.subash.productservice.model.Product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Paid holds are sold stock; they are only kept so a refund can give the stock back.
// Every day at 03:00 UTC, remove paid holds older than 7 days. This never changes any
// stock quantity and running it twice does nothing extra, so it needs no ShedLock.
@Component
@RequiredArgsConstructor
@Slf4j
public class HoldCleanupJob {

	private static final long KEEP_PAID_HOLDS_DAYS = 7;

	private final MongoTemplate mongoTemplate;

	@Scheduled(cron = "0 0 3 * * *", zone = "UTC")
	public void removeOldPaidHolds() {
		LocalDateTime cutoff = LocalDateTime.now().minusDays(KEEP_PAID_HOLDS_DAYS);
		// Spring stores LocalDateTime as a Date in the JVM's zone; the raw $pull needs the same Date
		Date cutoffDate = Date.from(cutoff.atZone(ZoneId.systemDefault()).toInstant());
		Document oldPaidHold = new Document("paid", true).append("createdAt", new Document("$lt", cutoffDate));
		UpdateResult result = mongoTemplate.updateMulti(Query.query(Criteria.where("holds").elemMatch(
				Criteria.where("paid").is(true).and("createdAt").lt(cutoff))), new Update().pull("holds", oldPaidHold),
				Product.class);
		log.info("Hold cleanup: removed old paid holds from {} products", result.getModifiedCount());
	}
}
