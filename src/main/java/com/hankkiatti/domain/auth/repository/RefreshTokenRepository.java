package com.hankkiatti.domain.auth.repository;

import com.hankkiatti.domain.auth.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken r set r.revokedAt = :now where r.account.id = :accountId and r.revokedAt is null")
    int revokeAllByAccountId(@Param("accountId") Long accountId, @Param("now") LocalDateTime now);

    /**
     * 만료된 refresh 토큰을 지운다 (매일 정리 작업). 회전마다 1행이 남아 지우지 않으면 사용자당 하루 수십 행씩 쌓인다.
     * 만료된 토큰은 폐기 여부와 관계없이 거절되므로(AuthTokenService.rotate — 만료 검사가 재사용 검사보다 먼저)
     * 지워도 재사용 감지에 빈틈이 생기지 않는다. 폐기됐지만 만료 전인 토큰은 재사용 감지를 위해 남긴다.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RefreshToken r where r.expiresAt <= :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
