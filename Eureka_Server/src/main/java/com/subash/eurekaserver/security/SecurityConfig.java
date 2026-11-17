package com.subash.eurekaserver.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

// Without a login anyone on the network could register a fake instance (for example
// a fake PRODUCT-SERVICE) and receive traffic. Clients send HTTP Basic credentials
// (EUREKA_USERNAME / EUREKA_PASSWORD) in their defaultZone URL.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
		// Eureka clients POST/PUT/DELETE to /eureka/** without a CSRF token; they are not browsers
		.csrf(csrf -> csrf.ignoringRequestMatchers("/eureka/**"))
		.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health", "/error").permitAll()
				.anyRequest().authenticated())
		// Basic for the clients; the dashboard in a browser also gets the Basic login prompt
		.httpBasic(Customizer.withDefaults());
		return http.build();
	}
}
