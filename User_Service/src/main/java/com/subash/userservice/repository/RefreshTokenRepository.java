package com.subash.userservice.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.subash.userservice.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	// Atomic "use it once": returns 1 only for the request that flips revoked from false to
	// true. Two refreshes with the same token at the same moment: the second gets 0.
	@Modifying
	@Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.id = :id AND t.revoked = false")
	int markUsed(@Param("id") Long id);

	@Modifying
	@Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.familyId = :familyId AND t.revoked = false")
	int revokeFamily(@Param("familyId") String familyId);

	@Modifying
	@Query("DELETE FROM RefreshToken t WHERE t.familyExpiresAt < :before")
	int deleteByFamilyExpiresAtBefore(@Param("before") LocalDateTime before);
}
