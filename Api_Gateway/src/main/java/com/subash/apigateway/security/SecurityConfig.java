package com.subash.apigateway.security;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

	@Bean
	public ReactiveJwtDecoder jwtDecoder(@Value("${jwt.secret}") String secret) {
		SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		return NimbusReactiveJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
	}

	// Only the frontend's own origins may call the API from a browser. They come from
	// app.cors.allowed-origins (comma-separated, in api-gateway.properties); empty = none.
	// allowCredentials: the browser may send the HttpOnly refresh-token cookie to /users/refresh and
	// /users/logout (fetch with credentials: "include"). With credentials the origins must be listed
	// exactly (no "*"); in dev that is only http://localhost:5501.
	@Bean
	public CorsConfigurationSource corsConfigurationSource(
			@Value("${app.cors.allowed-origins:}") List<String> allowedOrigins) {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(allowedOrigins);
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		cors.setAllowCredentials(true);
		// Browsers may cache a preflight answer for an hour
		cors.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", cors);
		return source;
	}

	@Bean
	public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
			JsonAuthenticationEntryPoint entryPoint) {
		return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
				// CORS runs inside the security chain, so the browser's OPTIONS preflight is answered
				// here, before authentication (it carries no token)
				.cors(Customizer.withDefaults())
				.securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
				.authorizeExchange(exchanges -> exchanges.pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.pathMatchers(HttpMethod.POST, "/users/login", "/users/register", "/users/refresh", "/users/logout").permitAll()
						.pathMatchers(HttpMethod.GET, "/products", "/products/**").permitAll()
						// Swagger UI and the services' OpenAPI specs (read-only; switched off in production)
						.pathMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**", "/webjars/swagger-ui/**",
								"/v3/api-docs/swagger-config", "/api-docs/**").permitAll()
						.pathMatchers("/actuator/health", "/error").permitAll().anyExchange().authenticated())
				.exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint)).oauth2ResourceServer(
						oauth2 -> oauth2.jwt(Customizer.withDefaults()).authenticationEntryPoint(entryPoint))
				.build();
	}
}