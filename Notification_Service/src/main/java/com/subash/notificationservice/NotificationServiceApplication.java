package com.subash.notificationservice;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude= {UserDetailsServiceAutoConfiguration.class})
public class NotificationServiceApplication {

	public static void main(String[] args) {
		// Always UTC, in Docker and in Eclipse alike: LocalDateTime.now() values from both end up in
		// the same databases, and the timeout jobs compare them, so they must use one zone
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(NotificationServiceApplication.class, args);
	}

}
