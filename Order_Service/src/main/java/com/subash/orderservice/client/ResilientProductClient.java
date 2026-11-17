package com.subash.orderservice.client;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.subash.orderservice.dto.ProductResponse;
import com.subash.orderservice.exception.ProductNotFoundException;
import com.subash.orderservice.exception.ProductServiceUnavailableException;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// The one way Order reads a product from Product Service: the Feign client, wrapped in a
// retry and a circuit breaker (settings and reasons in ResilienceConfig).
//
//   retry( circuitBreaker( feign call ) )
//
// Every real attempt is counted by the circuit breaker. When the breaker is open it throws
// CallNotPermittedException at once, which is not retried.
//
// Result for OrderService: the product, or
//   ProductNotFoundException           -> 400 (unknown product id)
//   ProductServiceUnavailableException -> 503 (Product down, slow, failing, or breaker open)
@Component
@RequiredArgsConstructor
@Slf4j
public class ResilientProductClient {

	private final ProductServiceClient productServiceClient;
	private final CircuitBreaker productServiceCircuitBreaker;
	private final Retry productServiceRetry;

	public ProductResponse getProduct(String productId) {
		Supplier<ProductResponse> call = () -> productServiceClient.getProductById(productId);
		Supplier<ProductResponse> guarded = CircuitBreaker.decorateSupplier(productServiceCircuitBreaker, call);
		Supplier<ProductResponse> withRetry = Retry.decorateSupplier(productServiceRetry, guarded);
		try {
			return withRetry.get();
		} catch (FeignException.NotFound e) {
			throw new ProductNotFoundException(productId);
		} catch (CallNotPermittedException e) {
			log.warn("Product service circuit breaker is open, not calling it for product {}", productId);
			throw new ProductServiceUnavailableException();
		} catch (FeignException e) {
			// RetryableException (no connection, timeout) is a FeignException too
			log.warn("Product service call failed for product {}: {}", productId, e.getMessage());
			throw new ProductServiceUnavailableException();
		}
	}
}
