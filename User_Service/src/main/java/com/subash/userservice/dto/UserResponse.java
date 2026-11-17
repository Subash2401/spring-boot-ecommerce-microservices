package com.subash.userservice.dto;

import java.util.Set;

import com.subash.userservice.entity.Role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
	
	public Long id;
	public String name;
	public String email;
	private Set<Role> roles;
}
