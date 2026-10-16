package com.hankkiatti.domain.auth.scheduler;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.auth.repository.PasswordResetTokenRepository;
import com.hankkiatti.domain.auth.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

@ExtendWith(MockitoExtension.class)
class TokenCleanupJobTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 4, 30);

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    private TokenCleanupJob tokenCleanupJob;

    @BeforeEach
    void setUp() {
        tokenCleanupJob = new TokenCleanupJob(refreshTokenRepository, passwordResetTokenRepository,
                Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));
    }

    @Test
    void run_현재시각으로두테이블의만료토큰삭제() {
        // given
        given(refreshTokenRepository.deleteExpired(NOW)).willReturn(3);
        given(passwordResetTokenRepository.deleteExpired(NOW)).willReturn(0);

        // when
        tokenCleanupJob.run();

        // then
        verify(refreshTokenRepository).deleteExpired(NOW);
        verify(passwordResetTokenRepository).deleteExpired(NOW);
    }

    @Test
    void cleanUp_refresh삭제가실패해도재설정토큰은삭제() {
        // given
        given(refreshTokenRepository.deleteExpired(NOW)).willThrow(new QueryTimeoutException("timeout"));

        // when
        tokenCleanupJob.cleanUp(NOW);

        // then
        verify(passwordResetTokenRepository).deleteExpired(NOW);
    }
}
