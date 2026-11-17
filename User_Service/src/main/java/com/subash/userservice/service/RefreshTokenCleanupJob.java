package com.subash.userservice.service;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.subash.userservice.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

// Every day at 04:00 UTC, delete refresh tokens of login sessions that ended (hit their
// 30-day maximum) more than a day ago. They can never be used again.
@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupJob {

	private final RefreshTokenRepository refreshTokenRepository;

	@Scheduled(cron = "0 0 4 * * *", zone = "UTC")
	@SchedulerLock(name = "refreshTokenCleanupJob", lockAtMostFor = "PT30M")
	@Transactional
	public void deleteEndedSessions() {
		int deleted = refreshTokenRepository.deleteByFamilyExpiresAtBefore(LocalDateTime.now().minusDays(1));
		log.info("Refresh token cleanup: deleted {} tokens", deleted);
	}
}
