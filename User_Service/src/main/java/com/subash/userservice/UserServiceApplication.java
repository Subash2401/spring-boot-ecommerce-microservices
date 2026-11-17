package com.subash.userservice;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UserServiceApplication {

	public static void main(String[] args) {
		// Always UTC, in Docker and in Eclipse alike: LocalDateTime.now() values from both end up in
		// the same databases, and the timeout jobs compare them, so they must use one zone
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(UserServiceApplication.class, args);
	}

}
