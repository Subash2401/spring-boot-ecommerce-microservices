package com.subash.userservice.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.subash.userservice.dto.AuthResult;
import com.subash.userservice.dto.LoginRequest;
import com.subash.userservice.dto.LoginResponse;
import com.subash.userservice.dto.Paging;
import com.subash.userservice.dto.RegisterRequest;
import com.subash.userservice.dto.UserResponse;
import com.subash.userservice.entity.Role;
import com.subash.userservice.entity.User;
import com.subash.userservice.exception.AccountLockedException;
import com.subash.userservice.exception.EmailAlreadyExistsException;
import com.subash.userservice.exception.InvalidRefreshTokenException;
import com.subash.userservice.repository.UserRepository;
import com.subash.userservice.security.CustomUserDetails;
import com.subash.userservice.kafka.UserEvent;
import com.subash.userservice.security.JwtUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {
	
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final JwtUtil jwtUtil;
	private final ApplicationEventPublisher eventPublisher;
	private final RefreshTokenService refreshTokenService;

	private static final int MAX_FAILED_LOGINS = 5;
	private static final long LOCK_MINUTES = 15;
	

	// One transaction: the user row and its UserRegistered outbox row are saved together
	@Override
	@Transactional
	public UserResponse registerUser(RegisterRequest request) {

		userRepository.findByEmail(request.getEmail())
		.ifPresent(u -> {
			throw new EmailAlreadyExistsException("Email already registered: "+request.getEmail());
			});

		User user = User.builder()
				.name(request.getName())
				.email(request.getEmail())
				.password(passwordEncoder.encode(request.getPassword()))
				.roles(Set.of(Role.USER))
				.eventPublished(true)
				.build();
		User saved = userRepository.save(user);
		// Written to the outbox just before commit (UserEventOutboxWriter), sent by OutboxPublisher
		eventPublisher.publishEvent(new UserEvent(saved.getId(), saved.getEmail(), UserEvent.USER_REGISTERED));

		return mapToResponse(saved);
	}
	
	@Override
	public AuthResult loginUser(LoginRequest request) {
		// null for an unknown email: Spring Security then answers with the same 401 as a wrong password
		User user = userRepository.findByEmail(request.getEmail()).orElse(null);

		// A locked account is refused before the password is checked
		if (user != null && user.isLocked()) {
			throw new AccountLockedException();
		}

		Authentication authentication;
		try {
			authentication = authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
		} catch (BadCredentialsException e) {
			if (user != null) {
				recordFailedLogin(user);
			}
			throw e;
		}

		// Successful login clears the failed-attempt count and any expired lock
		if (user != null && (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null)) {
			user.setFailedLoginAttempts(0);
			user.setLockedUntil(null);
			userRepository.save(user);
		}

		CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
		return tokensFor(userDetails, refreshTokenService.createForLogin(userDetails.getId()));
	}

	// New access token + new refresh token (the presented one is used up). The user is loaded
	// again, so changed roles or a lock take effect at the next refresh (within 30 minutes).
	@Override
	public AuthResult refresh(String refreshToken) {
		RefreshTokenService.IssuedToken next = refreshTokenService.rotate(refreshToken);
		User user = userRepository.findById(next.userId()).orElseThrow(InvalidRefreshTokenException::new);
		if (user.isLocked()) {
			throw new AccountLockedException();
		}
		return tokensFor(new CustomUserDetails(user), next);
	}

	@Override
	public void logout(String refreshToken) {
		refreshTokenService.revoke(refreshToken);
	}

	private AuthResult tokensFor(CustomUserDetails userDetails, RefreshTokenService.IssuedToken refreshToken) {
		long refreshSeconds = Math.max(0, Duration.between(LocalDateTime.now(), refreshToken.expiresAt()).getSeconds());
		LoginResponse body = new LoginResponse(jwtUtil.generateToken(userDetails), "Bearer",
				jwtUtil.getExpirationSeconds());
		return new AuthResult(body, refreshToken.value(), refreshSeconds);
	}

	// After MAX_FAILED_LOGINS wrong passwords in a row the account is locked for LOCK_MINUTES.
	// The counter restarts at 0, so after the lock ends the user gets MAX_FAILED_LOGINS new tries.
	private void recordFailedLogin(User user) {
		int attempts = user.getFailedLoginAttempts() + 1;
		if (attempts >= MAX_FAILED_LOGINS) {
			user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
			user.setFailedLoginAttempts(0);
			log.warn("Account {} locked for {} minutes after {} failed logins", user.getId(), LOCK_MINUTES, attempts);
		} else {
			user.setFailedLoginAttempts(attempts);
		}
		userRepository.save(user);
	}
	
	private UserResponse mapToResponse(User user) {
		
		return UserResponse.builder()
				.id(user.getId())
				.name(user.getName())
				.email(user.getEmail())
				.roles(user.getRoles())
				.build();
	}

	@Override
	public UserResponse getCurrentUser(String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email)); 
		
		return mapToResponse(user);
	}

	@Override
	// Oldest account first, by id (stable across pages)
	public Page<UserResponse> getAllUsers(Pageable pageable) {
		return userRepository.findAll(Paging.of(pageable, Sort.by("id"))).map(this::mapToResponse);
	}

}
