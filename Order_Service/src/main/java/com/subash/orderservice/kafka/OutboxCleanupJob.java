package com.subash.orderservice.kafka;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.subash.orderservice.model.OutboxStatus;
import com.subash.orderservice.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxCleanupJob {

	private final OutboxEventRepository outboxEventRepository;

	@Scheduled(cron = "0 0 0 1 * *", zone = "UTC")
	@SchedulerLock(name = "outboxCleanupJob", lockAtMostFor = "PT30M")
	public void deleteOldSentEvents() {
		LocalDateTime startOfThisMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
		int deleted = outboxEventRepository.deleteByStatusAndSentAtBefore(OutboxStatus.SENT, startOfThisMonth);
		log.info("Outbox cleanup: deleted {} sent events from before {}", deleted, startOfThisMonth);
	}
}