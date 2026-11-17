package com.subash.userservice.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.subash.userservice.dto.AuthResult;
import com.subash.userservice.dto.LoginRequest;
import com.subash.userservice.dto.LoginResponse;
import com.subash.userservice.exception.InvalidRefreshTokenException;
import com.subash.userservice.dto.RegisterRequest;
import com.subash.userservice.dto.UserResponse;
import com.subash.userservice.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
	
	private static final String REFRESH_COOKIE = "refresh_token";

	private final UserService userService;

	@Value("${app.auth.refresh-cookie-secure:false}")
	private boolean secureCookie;

	@PostMapping("/register")
	public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request){
		UserResponse response = userService.registerUser(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);			
	}
	
	// Body: the access token. The refresh token goes into an HttpOnly cookie.
	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request){
		AuthResult result = userService.loginUser(request);
		return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie(result).toString()).body(result.body());
	}

	// Reads the refresh token from the cookie; returns a new access token and sets a new cookie
	// (the old token is used up). 401 if the cookie is missing, invalid, expired or reused.
	@PostMapping("/refresh")
	public ResponseEntity<LoginResponse> refresh(
			@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken){
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new InvalidRefreshTokenException();
		}
		AuthResult result = userService.refresh(refreshToken);
		return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie(result).toString()).body(result.body());
	}

	// Ends the login session (revokes its refresh tokens) and deletes the cookie. Always 204.
	// The access token stays valid until it expires (at most 30 minutes), so the client must
	// also discard it.
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken){
		if (refreshToken != null && !refreshToken.isBlank()) {
			userService.logout(refreshToken);
		}
		ResponseCookie expired = cookieBuilder("").maxAge(0).build();
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, expired.toString()).build();
	}

	// HttpOnly: JavaScript cannot read it (protects it from XSS).
	// SameSite=Strict: the browser sends it only for requests from our own site (CSRF protection);
	// localhost:5501 and localhost:8080 count as the same site, ports do not matter.
	// Path=/users: a cookie can have only one path; this covers /users/refresh and /users/logout
	// (login, register and /me also receive it but ignore it).
	// Secure: only over HTTPS; off on localhost, on in production (app.auth.refresh-cookie-secure).
	private ResponseCookie refreshCookie(AuthResult result) {
		return cookieBuilder(result.refreshToken()).maxAge(result.refreshMaxAgeSeconds()).build();
	}

	private ResponseCookie.ResponseCookieBuilder cookieBuilder(String value) {
		return ResponseCookie.from(REFRESH_COOKIE, value).httpOnly(true).secure(secureCookie).sameSite("Strict")
				.path("/users");
	}
	
	@GetMapping("/me")
	public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication){
		String email = authentication.getName();
		UserResponse response = userService.getCurrentUser(email);
		return ResponseEntity.ok(response);
	}
	
	@PreAuthorize("hasRole('ADMIN')")
	@GetMapping("/all")
	// ?page=0&size=20 (size at most 50); by id
	public ResponseEntity<PagedModel<UserResponse>> getAllUsers(@PageableDefault(size = 20) Pageable pageable){
		return ResponseEntity.ok(new PagedModel<>(userService.getAllUsers(pageable)));
	}
	
}
