package com.subash.orderservice.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;

// Resilience4j settings for the calls from Order to Product Service (see ResilientProductClient).
//
// Retry: a short hiccup (Product restarting, one slow answer) should not fail the order.
//   Up to 3 attempts, 300 ms apart, but ONLY for temporary failures. A 404 ("no such
//   product") is an answer, not a failure: it is never retried.
// Circuit breaker: if Product is really down, stop calling it for a while instead of making
//   every order wait for timeouts. It looks at the last 10 calls; when at least 5 were made
//   and half of them failed, it "opens" for 30 s: calls fail at once (Order answers 503).
//   Then it lets 3 trial calls through; if they work, it closes again.
@Configuration
@Slf4j
public class ResilienceConfig {

	public static final String PRODUCT_SERVICE = "product-service";

	@Bean
	public Retry productServiceRetry() {
		RetryConfig config = RetryConfig.custom()
				.maxAttempts(3) // the first call + 2 retries
				.waitDuration(Duration.ofMillis(300))
				.retryOnException(ResilienceConfig::isTemporaryFailure)
				.build();
		return Retry.of(PRODUCT_SERVICE, config);
	}

	@Bean
	public CircuitBreaker productServiceCircuitBreaker() {
		CircuitBreakerConfig config = CircuitBreakerConfig.custom()
				.slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
				.slidingWindowSize(10)
				.minimumNumberOfCalls(5)
				.failureRateThreshold(50)
				.waitDurationInOpenState(Duration.ofSeconds(30))
				.permittedNumberOfCallsInHalfOpenState(3)
				// only temporary failures count; a 404 is a normal answer
				.recordException(ResilienceConfig::isTemporaryFailure)
				.build();
		CircuitBreaker circuitBreaker = CircuitBreaker.of(PRODUCT_SERVICE, config);
		// The only place the breaker's state shows up: one log line per change
		circuitBreaker.getEventPublisher().onStateTransition(
				event -> log.warn("Circuit breaker {}: {}", PRODUCT_SERVICE, event.getStateTransition()));
		return circuitBreaker;
	}

	// Temporary = could work on the next try: no connection, a timeout (RetryableException)
	// or a 5xx from Product. Every 4xx (404 included) is a real answer.
	static boolean isTemporaryFailure(Throwable error) {
		if (error instanceof RetryableException) {
			return true;
		}
		return error instanceof FeignException feignError && feignError.status() >= 500;
	}
}
