package com.subash.configserver.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

// Config Server hands out every service's DB passwords and the JWT secret,
// so every request needs HTTP Basic credentials (CONFIG_USERNAME / CONFIG_PASSWORD).
// Only the health check (for Docker) and /error stay open.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
		// Clients only send GET requests with Basic auth, no browser session, so CSRF is not needed
		.csrf(csrf -> csrf.disable())
		.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
		.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health", "/error").permitAll()
				.anyRequest().authenticated())
		.httpBasic(Customizer.withDefaults());
		return http.build();
	}
}
