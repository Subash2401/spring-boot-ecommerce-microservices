package com.subash.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
	
	@NotBlank(message = "Name is required")
	@Size(max = 100, message = "Name cannot be more than 100 characters")
	public String name;

	@NotBlank(message = "Email is required")
	@Email(message = "Email must be a valid format")
	@Size(max = 254, message = "Email cannot be more than 254 characters")
	public String email;

	// BCrypt only uses the first 72 bytes, and Spring Security rejects longer passwords
	@NotBlank(message = "Password is required")
	@Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
	public String password;
}
