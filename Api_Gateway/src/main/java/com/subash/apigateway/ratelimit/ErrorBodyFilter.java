package com.subash.apigateway.ratelimit;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.subash.apigateway.exception.ErrorResponse;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

// Two gateway filters end the response with a status but no body: RequestRateLimiter (429)
// and RequestSize (413). This filter wraps the response so that, when it ends with one of
// those statuses, a JSON ErrorResponse is written instead, like every other error.
@Component
@RequiredArgsConstructor
public class ErrorBodyFilter implements WebFilter {

	private static final int TOO_MANY_REQUESTS = 429;
	private static final int CONTENT_TOO_LARGE = 413;

	private final JsonMapper jsonMapper;

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		ServerHttpResponse response = new ServerHttpResponseDecorator(exchange.getResponse()) {
			@Override
			public Mono<Void> setComplete() {
				String message = messageFor(getStatusCode());
				if (message == null) {
					return super.setComplete();
				}
				getHeaders().setContentType(MediaType.APPLICATION_JSON);
				byte[] body = jsonMapper.writeValueAsBytes(new ErrorResponse(getStatusCode().value(), message));
				return writeWith(Mono.just(bufferFactory().wrap(body)));
			}
		};
		return chain.filter(exchange.mutate().response(response).build());
	}

	private static String messageFor(HttpStatusCode status) {
		if (status == null) {
			return null;
		}
		return switch (status.value()) {
		case TOO_MANY_REQUESTS -> "Too many requests, please try again later";
		case CONTENT_TOO_LARGE -> "Request body is too large (maximum 1 MB)";
		default -> null;
		};
	}
}
