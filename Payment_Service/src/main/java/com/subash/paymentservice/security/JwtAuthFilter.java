package com.subash.paymentservice.security;

import java.io.IOException;

import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

	private final JwtUtil jwtUtil;

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {

		String authHeader = request.getHeader("Authorization");
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
			return;
		}

		try {
			Claims claims = jwtUtil.parseClaims(authHeader.substring(7));
			Long userId = jwtUtil.extractUserId(claims);

			if (userId != null) {
				// Principal is the userId, so controllers can use @AuthenticationPrincipal Long userId
				UsernamePasswordAuthenticationToken authToken =
						new UsernamePasswordAuthenticationToken(userId, null, jwtUtil.extractAuthorities(claims));
				SecurityContextHolder.getContext().setAuthentication(authToken);
			} else {
				log.warn("JWT without userId claim rejected");
			}
		} catch (JwtException | IllegalArgumentException ex) {
			log.warn("Invalid JWT rejected: {}", ex.getMessage());
		}

		filterChain.doFilter(request, response);
	}
}
