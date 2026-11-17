package com.subash.userservice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.subash.userservice.dto.AuthResult;
import com.subash.userservice.dto.LoginRequest;
import com.subash.userservice.dto.RegisterRequest;
import com.subash.userservice.dto.UserResponse;

public interface UserService {
	
	UserResponse registerUser(RegisterRequest request);
	AuthResult loginUser(LoginRequest request);
	AuthResult refresh(String refreshToken);
	void logout(String refreshToken);
	UserResponse getCurrentUser(String email);
	Page<UserResponse> getAllUsers(Pageable pageable);
}
