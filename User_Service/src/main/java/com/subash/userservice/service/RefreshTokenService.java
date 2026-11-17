package com.subash.userservice.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.subash.userservice.entity.RefreshToken;
import com.subash.userservice.exception.InvalidRefreshTokenException;
import com.subash.userservice.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Refresh tokens: random 256-bit values, stored only as SHA-256 hashes.
// - login: a new family (one login session)
// - refresh: the presented token is used up and replaced by a new one in the same family
// - a token that was already used is presented again: it was probably stolen, so the
//   whole family is revoked and the user must log in again
// - logout: the family is revoked
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final RefreshTokenRepository refreshTokenRepository;

	// Each refresh token is valid this long unless used earlier
	@Value("${jwt.refresh-idle-days:7}")
	private long idleDays;

	// A login session ends this long after login, however often it is refreshed
	@Value("${jwt.refresh-max-days:30}")
	private long maxDays;

	public record IssuedToken(String value, Long userId, LocalDateTime expiresAt) {
	}

	@Transactional
	public IssuedToken createForLogin(Long userId) {
		LocalDateTime now = LocalDateTime.now();
		return issue(userId, UUID.randomUUID().toString(), now.plusDays(maxDays));
	}

	// Uses up the presented token and returns a new one in the same family.
	// Throws InvalidRefreshTokenException (401) for unknown, expired, revoked or reused tokens.
	@Transactional(noRollbackFor = InvalidRefreshTokenException.class)
	public IssuedToken rotate(String presentedToken) {
		RefreshToken token = refreshTokenRepository.findByTokenHash(hash(presentedToken))
				.orElseThrow(InvalidRefreshTokenException::new);
		LocalDateTime now = LocalDateTime.now();
		if (token.isRevoked() || refreshTokenRepository.markUsed(token.getId()) == 0) {
			// Already used or revoked: treat it as stolen and end the whole session
			int revoked = refreshTokenRepository.revokeFamily(token.getFamilyId());
			log.warn("Refresh token reuse for user {}: revoked {} tokens in family {}", token.getUserId(), revoked,
					token.getFamilyId());
			throw new InvalidRefreshTokenException();
		}
		if (token.getExpiresAt().isBefore(now) || token.getFamilyExpiresAt().isBefore(now)) {
			throw new InvalidRefreshTokenException();
		}
		return issue(token.getUserId(), token.getFamilyId(), token.getFamilyExpiresAt());
	}

	// Logout. Unknown tokens are ignored, so logout always succeeds and reveals nothing.
	@Transactional
	public void revoke(String presentedToken) {
		refreshTokenRepository.findByTokenHash(hash(presentedToken))
				.ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId()));
	}

	private IssuedToken issue(Long userId, String familyId, LocalDateTime familyExpiresAt) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

		LocalDateTime now = LocalDateTime.now();
		LocalDateTime idleEnd = now.plusDays(idleDays);
		RefreshToken token = new RefreshToken();
		token.setUserId(userId);
		token.setTokenHash(hash(value));
		token.setFamilyId(familyId);
		token.setCreatedAt(now);
		token.setExpiresAt(idleEnd.isBefore(familyExpiresAt) ? idleEnd : familyExpiresAt);
		token.setFamilyExpiresAt(familyExpiresAt);
		refreshTokenRepository.save(token);
		return new IssuedToken(value, userId, token.getExpiresAt());
	}

	private static String hash(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}
}
