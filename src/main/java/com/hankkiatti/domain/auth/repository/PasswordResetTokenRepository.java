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
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 만료된 재설정 토큰을 지운다 (매일 정리 작업). 유효 시간이 30분이라 만료 뒤에는 쓸 수 없고,
     * 재발송 간격 확인(PasswordResetService.RESEND_INTERVAL = 10분)도 만료된 토큰에는 닿지 않으므로 지워도 동작이 바뀌지 않는다.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PasswordResetToken t where t.expiresAt <= :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
