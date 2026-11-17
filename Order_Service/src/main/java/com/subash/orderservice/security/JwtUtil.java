package com.subash.orderservice.security;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

	@Value("${jwt.secret}")
	private String secret;

	private SecretKey getSigninKey() {
		return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	// Throws JwtException if the signature is wrong or the token has expired
	public Claims parseClaims(String token) {
		return Jwts.parser()
				.verifyWith(getSigninKey())
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	// A small JSON number comes back as Integer, a big one as Long, so read it as Number
	public Long extractUserId(Claims claims) {
		Number userId = claims.get("userId", Number.class);
		return userId == null ? null : userId.longValue();
	}

	public List<SimpleGrantedAuthority> extractAuthorities(Claims claims) {
		List<?> roles = claims.get("roles", List.class);
		if (roles == null) {
			return List.of();
		}
		return roles.stream()
				.map(role -> new SimpleGrantedAuthority(role.toString()))
				.toList();
	}
}
