package com.hankkiatti.domain.auth.repository;

import com.hankkiatti.domain.auth.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    Optional<PasswordResetToken> findTopByAccountIdOrderByIdDesc(Long accountId);

    @Query("select t.account.id from PasswordResetToken t where t.tokenHash = :tokenHash")
    Optional<Long> findAccountIdByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * 토큰 행을 잠그고 가져온다. 잠금 조회는 항상 마지막으로 커밋된 값을 읽으므로, 먼저 끝난 요청이 쓴 사용 기록을 놓치지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t where t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PasswordResetToken t set t.usedAt = :now where t.account.id = :accountId and t.usedAt is null")
    int invalidateAllByAccountId(@Param("accountId") Long accountId, @Param("now") LocalDateTime now);
}
