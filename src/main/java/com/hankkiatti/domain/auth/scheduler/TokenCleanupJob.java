package com.hankkiatti.domain.auth.scheduler;

import com.hankkiatti.domain.auth.repository.PasswordResetTokenRepository;
import com.hankkiatti.domain.auth.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.ToIntFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 만료된 refresh 토큰·비밀번호 재설정 토큰을 매일 새벽에 지운다. 만료 뒤에는 어느 쪽도 쓸 수 없어 지워도 동작이 바뀌지 않는다.
 * 지우기는 조건부 DELETE 한 문장이라 서버가 여러 대여도 같은 행을 두 번 지우는 일이 없고(한쪽은 0건), 놓친 날은 다음 실행이 따라잡는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "auth.token-cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class TokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final Clock clock;

    // 새벽 4시 30분 — 식사 자동 처리(매분)·메일·문자 발송과 겹치지 않는 한가한 시각
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void run() {
        cleanUp(LocalDateTime.now(clock));
    }

    public void cleanUp(LocalDateTime now) {
        // 테이블마다 별도 트랜잭션(리포지토리 메서드의 @Transactional)이라 한쪽이 실패해도 다른 쪽은 지워진다
        delete("refresh 토큰", now, refreshTokenRepository::deleteExpired);
        delete("비밀번호 재설정 토큰", now, passwordResetTokenRepository::deleteExpired);
    }

    private void delete(String target, LocalDateTime now, ToIntFunction<LocalDateTime> action) {
        try {
            int deleted = action.applyAsInt(now);
            if (deleted > 0) {
                log.info("만료된 {} {}건 삭제", target, deleted);
            }
        } catch (RuntimeException e) {
            // 토큰 테이블에는 해시·시각만 있어 예외 메시지에 개인정보가 들어가지 않는다
            log.error("만료된 {} 삭제 실패", target, e);
        }
    }
}
