package com.hankkiatti.domain.sms.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SmsOutboxTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 12, 0);
    private static final List<Duration> RETRY_DELAYS =
            List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30));

    private SmsOutbox newSms() {
        return new SmsOutbox(SmsType.MATCHED, "+821012345678", "[한끼아띠] 10/12 12:00 도우미가 정해졌어요", 7L, NOW);
    }

    @Test
    void 생성자_대기상태로바로발송가능() {
        // when
        SmsOutbox sms = newSms();

        // then
        assertThat(sms.getStatus()).isEqualTo(SmsOutboxStatus.PENDING);
        assertThat(sms.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(sms.getAttempts()).isZero();
    }

    @Test
    void markSent_발송완료_본문을지움() {
        // given
        SmsOutbox sms = newSms();

        // when
        sms.markSent(NOW.plusSeconds(3));

        // then
        assertThat(sms.getStatus()).isEqualTo(SmsOutboxStatus.SENT);
        assertThat(sms.getSentAt()).isEqualTo(NOW.plusSeconds(3));
        assertThat(sms.getBody()).isEmpty();
    }

    @Test
    void recordFailure_재시도간격대로다시예약하고_다쓰면최종실패() {
        // given
        SmsOutbox sms = newSms();

        // when & then
        sms.recordFailure("throttled", NOW, RETRY_DELAYS);
        assertThat(sms.getStatus()).isEqualTo(SmsOutboxStatus.PENDING);
        assertThat(sms.getNextAttemptAt()).isEqualTo(NOW.plusMinutes(1));
        sms.recordFailure("throttled", NOW, RETRY_DELAYS);
        assertThat(sms.getNextAttemptAt()).isEqualTo(NOW.plusMinutes(5));
        sms.recordFailure("throttled", NOW, RETRY_DELAYS);
        assertThat(sms.getNextAttemptAt()).isEqualTo(NOW.plusMinutes(30));
        assertThat(sms.getBody()).isNotEmpty();

        sms.recordFailure("throttled", NOW, RETRY_DELAYS);
        assertThat(sms.isFailed()).isTrue();
        assertThat(sms.getAttempts()).isEqualTo(4);
        assertThat(sms.getBody()).isEmpty();
    }

    @Test
    void recordFailure_긴오류메시지_500자로자름() {
        // given
        SmsOutbox sms = newSms();

        // when
        sms.recordFailure("x".repeat(600), NOW, RETRY_DELAYS);

        // then
        assertThat(sms.getLastError()).hasSize(500);
    }
}
