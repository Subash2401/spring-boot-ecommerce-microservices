package com.subash.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

// Scheduled jobs run on only one instance at a time. The lock is a document in the
// "shedLock" collection of notificationdb, which MongoDB creates on first use.
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT1M")
public class ShedLockConfig {

	@Bean
	public LockProvider lockProvider(MongoTemplate mongoTemplate) {
		return new MongoLockProvider(mongoTemplate.getDb());
	}
}
