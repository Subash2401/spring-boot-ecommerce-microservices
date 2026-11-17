package com.subash.userservice.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// One refresh token. Only its SHA-256 hash is stored, so a database leak does not leak
// usable tokens. All tokens from one login share a familyId: each refresh replaces the
// token with a new one in the same family, and logout or a reused token revokes the family.
@Entity
@Table(name = "refresh_tokens", indexes = { @Index(name = "idx_refresh_family", columnList = "familyId"),
		@Index(name = "idx_refresh_family_expires", columnList = "familyExpiresAt") })
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	// SHA-256 of the token, hex
	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(nullable = false, length = 36)
	private String familyId;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	// This token's own end (7 days by default, never after familyExpiresAt)
	@Column(nullable = false)
	private LocalDateTime expiresAt;

	// The login session's hard end (30 days after login by default); rotation does not extend it
	@Column(nullable = false)
	private LocalDateTime familyExpiresAt;

	// Set when the token is used (rotated) or revoked (logout, reuse detected)
	@Column(nullable = false)
	private boolean revoked;
}
