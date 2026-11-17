package com.subash.userservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.subash.userservice.entity.RefreshToken;
import com.subash.userservice.exception.InvalidRefreshTokenException;
import com.subash.userservice.repository.RefreshTokenRepository;
import com.subash.userservice.service.RefreshTokenService.IssuedToken;

// Unit tests for refresh-token rotation. No Spring, no database: Mockito fakes the repository.
// (RefreshTokenIntegrationTest checks the same rules against a real MySQL.)
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	@InjectMocks
	private RefreshTokenService refreshTokenService;

	@BeforeEach
	void setUp() {
		// The @Value fields Spring would fill from jwt.refresh-idle-days / jwt.refresh-max-days
		ReflectionTestUtils.setField(refreshTokenService, "idleDays", 7L);
		ReflectionTestUtils.setField(refreshTokenService, "maxDays", 30L);
	}

	@Test
	void createForLogin_storesOnlyAHashOfTheToken_validFor7Days() {
		IssuedToken issued = refreshTokenService.createForLogin(7L);

		RefreshToken saved = savedToken();
		// the database never sees the token itself, only its SHA-256 (64 hex characters)
		assertThat(saved.getTokenHash()).hasSize(64).isNotEqualTo(issued.value());
		assertThat(saved.getExpiresAt()).isBetween(LocalDateTime.now().plusDays(7).minusMinutes(1),
				LocalDateTime.now().plusDays(7));
		// the whole login session ends after 30 days, however often it is refreshed
		assertThat(saved.getFamilyExpiresAt()).isAfter(LocalDateTime.now().plusDays(29));
	}

	@Test
	void rotate_validToken_usesItUp_andGivesANewOneInTheSameFamily() {
		RefreshToken current = token(false, LocalDateTime.now().plusDays(1));
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(current));
		when(refreshTokenRepository.markUsed(current.getId())).thenReturn(1); // we were first

		IssuedToken next = refreshTokenService.rotate("presented-token");

		assertThat(next.value()).isNotBlank().isNotEqualTo("presented-token");
		assertThat(savedToken().getFamilyId()).isEqualTo(current.getFamilyId());
	}

	@Test
	void rotate_tokenThatWasAlreadyUsed_endsTheWholeSession() {
		// Someone presents a token that was already rotated: maybe it was stolen. The safe
		// answer is to revoke every token of that login, so the thief's copy dies too.
		RefreshToken used = token(false, LocalDateTime.now().plusDays(1));
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(used));
		when(refreshTokenRepository.markUsed(used.getId())).thenReturn(0); // someone used it before us

		assertThatThrownBy(() -> refreshTokenService.rotate("presented-token"))
				.isInstanceOf(InvalidRefreshTokenException.class);
		verify(refreshTokenRepository).revokeFamily(used.getFamilyId());
		verify(refreshTokenRepository, never()).save(any());
	}

	@Test
	void rotate_expiredToken_isRejected() {
		RefreshToken expired = token(false, LocalDateTime.now().minusMinutes(1));
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));
		when(refreshTokenRepository.markUsed(expired.getId())).thenReturn(1);

		assertThatThrownBy(() -> refreshTokenService.rotate("presented-token"))
				.isInstanceOf(InvalidRefreshTokenException.class);
		verify(refreshTokenRepository, never()).save(any());
	}

	@Test
	void rotate_unknownToken_isRejected() {
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> refreshTokenService.rotate("made-up-token"))
				.isInstanceOf(InvalidRefreshTokenException.class);
	}

	@Test
	void revoke_unknownToken_doesNothing_soLogoutAlwaysSucceeds() {
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

		refreshTokenService.revoke("made-up-token");

		verify(refreshTokenRepository, never()).revokeFamily(anyString());
	}

	private static RefreshToken token(boolean revoked, LocalDateTime expiresAt) {
		RefreshToken token = new RefreshToken();
		token.setId(1L);
		token.setUserId(7L);
		token.setFamilyId("family-1");
		token.setTokenHash("x".repeat(64));
		token.setCreatedAt(LocalDateTime.now().minusDays(1));
		token.setExpiresAt(expiresAt);
		token.setFamilyExpiresAt(LocalDateTime.now().plusDays(20));
		token.setRevoked(revoked);
		return token;
	}

	private RefreshToken savedToken() {
		ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
		verify(refreshTokenRepository).save(captor.capture());
		return captor.getValue();
	}
}
