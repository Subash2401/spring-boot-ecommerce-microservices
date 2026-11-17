package com.subash.apigateway.security;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.subash.apigateway.exception.ErrorResponse;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class JsonAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

	private final JsonMapper jsonMapper;

	@Override
	public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {

		HttpStatus status = HttpStatus.UNAUTHORIZED;
		String errorCode = null;
		String message = "Missing or invalid authorization header";
		if (ex instanceof OAuth2AuthenticationException oauthEx) {
			OAuth2Error error = oauthEx.getError();
			errorCode = error.getErrorCode();
			if (error instanceof BearerTokenError bearerError) {
				status = bearerError.getHttpStatus();
			}
			message = BearerTokenErrorCodes.INVALID_TOKEN.equals(errorCode) ? "Invalid or expired token"
					: "Invalid authorization request";
		}

		ServerHttpResponse response = exchange.getResponse();
		response.setStatusCode(status);
		response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE,
				errorCode == null ? "Bearer" : "Bearer error=\"" + errorCode + "\"");
		response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

		byte[] body = jsonMapper.writeValueAsBytes(new ErrorResponse(status.value(), message));
		DataBuffer buffer = response.bufferFactory().wrap(body);
		return response.writeWith(Mono.just(buffer));
	}

}
