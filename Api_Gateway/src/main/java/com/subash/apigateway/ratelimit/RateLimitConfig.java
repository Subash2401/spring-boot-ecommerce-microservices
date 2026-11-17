package com.subash.apigateway.ratelimit;

import java.net.InetSocketAddress;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import reactor.core.publisher.Mono;

// Rate limits are counted per client IP. The limits themselves (RequestRateLimiter
// filters) are in api-gateway.properties in the config repo.
// Behind a cloud load balancer the real client IP is in X-Forwarded-For; that is
// handled at deploy time.
@Configuration
public class RateLimitConfig {

	// Default for every route: the client IP
	@Primary
	@Bean
	public KeyResolver ipKeyResolver() {
		return exchange -> Mono.just(clientIp(exchange.getRequest().getRemoteAddress()));
	}

	// Login and register use their own key, so their strict limit gets its own Redis
	// bucket instead of sharing the general per-IP bucket
	@Bean
	public KeyResolver authKeyResolver() {
		return exchange -> Mono.just("auth:" + clientIp(exchange.getRequest().getRemoteAddress()));
	}

	private static String clientIp(InetSocketAddress address) {
		// An empty key would make the rate limiter reject the request, so fall back to a fixed key
		if (address == null || address.getAddress() == null) {
			return "unknown";
		}
		return address.getAddress().getHostAddress();
	}
}
