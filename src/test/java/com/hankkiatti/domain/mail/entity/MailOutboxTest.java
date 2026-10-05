package com.hankkiatti.domain.mail.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MailOutboxTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 12, 0);
    private static final List<Duration> RETRY_DELAYS =
            List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30));

    private MailOutbox newMail() {
        return new MailOutbox(MailType.STUDENT_CREDENTIAL, "60231234@mju.ac.kr", "계정 안내", "초기 비밀번호: abc", 7L, NOW);
    }

    @Test
    void 생성자_대기상태로바로발송가능() {
        // when
        MailOutbox mail = newMail();

        // then
        assertThat(mail.getStatus()).isEqualTo(MailOutboxStatus.PENDING);
        assertThat(mail.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(mail.getAttempts()).isZero();
    }

    @Test
    void markSent_발송완료_본문을지움() {
        // given
        MailOutbox mail = newMail();

        // when
        mail.markSent(NOW.plusSeconds(3));

        // then
        assertThat(mail.getStatus()).isEqualTo(MailOutboxStatus.SENT);
        assertThat(mail.getSentAt()).isEqualTo(NOW.plusSeconds(3));
        assertThat(mail.getBody()).isEmpty();
    }

    @Test
    void recordFailure_재시도간격대로다시예약() {
        // given
        MailOutbox mail = newMail();

        // when & then
        mail.recordFailure("timeout", NOW, RETRY_DELAYS);
        assertThat(mail.getStatus()).isEqualTo(MailOutboxStatus.PENDING);
        assertThat(mail.getNextAttemptAt()).isEqualTo(NOW.plusMinutes(1));

        mail.recordFailure("timeout", NOW, RETRY_DELAYS);
        assertThat(mail.getNextAttemptAt()).isEqualTo(NOW.plusMinutes(5));

        mail.recordFailure("timeout", NOW, RETRY_DELAYS);
        assertThat(mail.getNextAttemptAt()).isEqualTo(NOW.plusMinutes(30));
        assertThat(mail.getBody()).isNotEmpty();
    }

    @Test
    void recordFailure_재시도를다쓰면_최종실패하고본문을지움() {
        // given
        MailOutbox mail = newMail();
        RETRY_DELAYS.forEach(delay -> mail.recordFailure("timeout", NOW, RETRY_DELAYS));

        // when
        mail.recordFailure("timeout", NOW, RETRY_DELAYS);

        // then
        assertThat(mail.isFailed()).isTrue();
        assertThat(mail.getAttempts()).isEqualTo(4);
        assertThat(mail.getBody()).isEmpty();
    }

    @Test
    void recordFailure_긴오류메시지_500자로자름() {
        // given
        MailOutbox mail = newMail();

        // when
        mail.recordFailure("x".repeat(800), NOW, RETRY_DELAYS);

        // then
        assertThat(mail.getLastError()).hasSize(500);
    }
}
