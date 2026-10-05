package com.hankkiatti.domain.auth.repository;

import com.hankkiatti.domain.auth.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken r set r.revokedAt = :now where r.account.id = :accountId and r.revokedAt is null")
    int revokeAllByAccountId(@Param("accountId") Long accountId, @Param("now") LocalDateTime now);
}
