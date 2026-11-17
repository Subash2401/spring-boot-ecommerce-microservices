package com.subash.orderservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.subash.orderservice.config.ResilienceConfig;
import com.subash.orderservice.dto.ProductResponse;
import com.subash.orderservice.exception.ProductNotFoundException;
import com.subash.orderservice.exception.ProductServiceUnavailableException;

import feign.FeignException;
import feign.Request;
import feign.Response;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

// Retry and circuit breaker around the Product Service call. The Feign client is a Mockito
// mock; the Retry and CircuitBreaker are the real ones from ResilienceConfig (new ones for
// every test, so one test's failures do not open the breaker for the next).
@ExtendWith(MockitoExtension.class)
class ResilientProductClientTest {

	@Mock
	private ProductServiceClient productServiceClient;

	private CircuitBreaker circuitBreaker;
	private ResilientProductClient client;

	@BeforeEach
	void setUp() {
		ResilienceConfig config = new ResilienceConfig();
		circuitBreaker = config.productServiceCircuitBreaker();
		client = new ResilientProductClient(productServiceClient, circuitBreaker, config.productServiceRetry());
	}

	@Test
	void oneTemporaryFailure_isRetried_andTheOrderStillWorks() {
		when(productServiceClient.getProductById("p1"))
				.thenThrow(feignError(503)) // Product restarting ...
				.thenReturn(product("p1")); // ... and back 300 ms later

		assertThat(client.getProduct("p1").getId()).isEqualTo("p1");
		verify(productServiceClient, times(2)).getProductById("p1");
	}

	@Test
	void unknownProduct_isNotRetried_andGivesProductNotFound() { // -> 400
		when(productServiceClient.getProductById("nope")).thenThrow(feignError(404));

		assertThatThrownBy(() -> client.getProduct("nope")).isInstanceOf(ProductNotFoundException.class);
		verify(productServiceClient, times(1)).getProductById("nope");
	}

	@Test
	void productKeepsFailing_isTriedThreeTimes_thenGivesServiceUnavailable() { // -> 503
		when(productServiceClient.getProductById("p1")).thenThrow(feignError(500));

		assertThatThrownBy(() -> client.getProduct("p1")).isInstanceOf(ProductServiceUnavailableException.class);
		verify(productServiceClient, times(3)).getProductById("p1");
	}

	@Test
	void afterRepeatedFailures_theBreakerOpens_andProductIsNotCalledAnyMore() {
		when(productServiceClient.getProductById("p1")).thenThrow(feignError(500));

		// two orders, each failing after its retries: 5 failed calls are enough to open it
		assertThatThrownBy(() -> client.getProduct("p1")).isInstanceOf(ProductServiceUnavailableException.class);
		assertThatThrownBy(() -> client.getProduct("p1")).isInstanceOf(ProductServiceUnavailableException.class);
		assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

		// the next order gets 503 at once, without waiting for Product
		clearInvocations(productServiceClient);
		assertThatThrownBy(() -> client.getProduct("p1")).isInstanceOf(ProductServiceUnavailableException.class);
		verifyNoInteractions(productServiceClient);
	}

	private static ProductResponse product(String id) {
		ProductResponse product = new ProductResponse();
		product.setId(id);
		return product;
	}

	// The exception Feign throws for an HTTP error status (404 -> FeignException.NotFound, ...)
	private static FeignException feignError(int status) {
		Request request = Request.create(Request.HttpMethod.GET, "/products/x", Map.of(), null,
				StandardCharsets.UTF_8, null);
		Response response = Response.builder().status(status).reason("test").request(request).headers(Map.of())
				.build();
		return FeignException.errorStatus("getProductById", response);
	}
}
