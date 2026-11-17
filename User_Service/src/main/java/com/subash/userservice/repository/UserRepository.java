package com.subash.userservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.subash.userservice.entity.Role;
import com.subash.userservice.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
	
	Optional<User> findByEmail(String email);

	List<User> findByEventPublishedFalse();

	@Query("SELECT COUNT(u) > 0 FROM User u JOIN u.roles r WHERE r = :role")
	boolean existsByRole(@Param("role") Role role);

}
