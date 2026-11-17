package com.subash.userservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.subash.userservice.exception.InvalidRefreshTokenException;
import com.subash.userservice.service.RefreshTokenService.IssuedToken;

// Refresh-token rotation against a real MySQL 8.4 that Testcontainers starts in Docker
// (Docker must be running). The database starts EMPTY: Flyway runs V1__initial_schema.sql
// and ddl-auto=validate checks every entity (User, RefreshToken, OutboxEvent) against it.
// @Import adds RefreshTokenService to the database-only @DataJpaTest context.
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(RefreshTokenService.class)
@Testcontainers
class RefreshTokenIntegrationTest {

	@Container
	@ServiceConnection
	static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	private RefreshTokenService refreshTokenService;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void flywayCreatesTheWholeSchema_onAnEmptyDatabase() {
		Integer v1 = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1", Integer.class);
		assertThat(v1).isEqualTo(1);
	}

	@Test
	void loginThenRefresh_works_andTheOldTokenCannotBeUsedAgain() {
		IssuedToken login = refreshTokenService.createForLogin(7L);

		IssuedToken refreshed = refreshTokenService.rotate(login.value());

		assertThat(refreshed.value()).isNotEqualTo(login.value());
		assertThatThrownBy(() -> refreshTokenService.rotate(login.value()))
				.isInstanceOf(InvalidRefreshTokenException.class);
	}

	@Test
	void reusingAnOldToken_alsoKillsTheNewestToken() {
		// The attacker replays the old token after the user has already refreshed:
		// the whole family is revoked, so the user's newest token stops working as well.
		IssuedToken login = refreshTokenService.createForLogin(7L);
		IssuedToken newest = refreshTokenService.rotate(login.value());

		assertThatThrownBy(() -> refreshTokenService.rotate(login.value()))
				.isInstanceOf(InvalidRefreshTokenException.class);
		assertThatThrownBy(() -> refreshTokenService.rotate(newest.value()))
				.isInstanceOf(InvalidRefreshTokenException.class);
	}

	@Test
	void logout_revokesTheSession() {
		IssuedToken login = refreshTokenService.createForLogin(7L);

		refreshTokenService.revoke(login.value());

		assertThatThrownBy(() -> refreshTokenService.rotate(login.value()))
				.isInstanceOf(InvalidRefreshTokenException.class);
	}
}
