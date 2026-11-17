package com.subash.userservice.entity;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name="users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@Column(nullable = false)
	private String name;
	
	@Column(nullable = false, unique = true)
	private String email;
	
	@Column(nullable = false)
	private String password;
	
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name="user_id"))
	// Plain VARCHAR, so Hibernate does not create a MySQL ENUM column that blocks new roles
	@Column(name="role", length = 30)
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Builder.Default
	private Set<Role> roles=new HashSet<>();

	// Wrong passwords in a row; reset on a successful login and when the account gets locked
	@Column(nullable = false)
	private int failedLoginAttempts;

	// While this is in the future, login returns 423 without checking the password
	private LocalDateTime lockedUntil;

	// true once a UserRegistered event for this user is in the outbox (see UserEventBackfill).
	// "default false" lets MySQL add the column to existing rows, which then get backfilled.
	@Builder.Default
	@Column(nullable = false, columnDefinition = "boolean default false")
	private boolean eventPublished = false;

	public boolean isLocked() {
		return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
	}
}
