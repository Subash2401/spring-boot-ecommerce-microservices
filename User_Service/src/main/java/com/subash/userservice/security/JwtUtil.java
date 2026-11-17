package com.subash.userservice.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {
	
	@Value("${jwt.secret}")
	private String secret;
	
	@Value("${jwt.expiration}")
	private long expirationMs;
	
	// Access token lifetime in seconds, for the expiresIn field of the login response
	public long getExpirationSeconds() {
		return expirationMs / 1000;
	}

	private SecretKey getSigninKey() {
		return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}
	
	public String generateToken(CustomUserDetails userDetails) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + expirationMs);

		// e.g. ["ROLE_USER"]; the other services read userId and roles from the token
		List<String> roles = userDetails.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.toList();

	return Jwts.builder()
			.subject(userDetails.getUsername())
			.claim("userId", userDetails.getId())
			.claim("roles", roles)
			.issuedAt(now)
			.expiration(expiry)
			.signWith(getSigninKey(), Jwts.SIG.HS256)
			.compact();
	}
	
	private Claims parseClaims(String token) {
		return Jwts.parser()
				.verifyWith(getSigninKey())
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}
	
	public String extractUsername(String token) {
		return parseClaims(token).getSubject();
	}
	
	private boolean isTokenExpired(String token) {
		return parseClaims(token).getExpiration().before(new Date());
	}
	
	public boolean isTokenValid(String token, UserDetails userDetails) {
		String username = extractUsername(token);
		return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
	}
	
}
