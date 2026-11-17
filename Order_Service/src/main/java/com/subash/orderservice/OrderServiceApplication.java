package com.subash.orderservice;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
@EnableFeignClients
@EnableScheduling
public class OrderServiceApplication {

	public static void main(String[] args) {
		// Always UTC, in Docker and in Eclipse alike: LocalDateTime.now() values from both end up in
		// the same databases, and the timeout jobs compare them, so they must use one zone
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(OrderServiceApplication.class, args);
	}

}
