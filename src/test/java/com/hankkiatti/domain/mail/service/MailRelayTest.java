package com.hankkiatti.domain.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hankkiatti.domain.mail.entity.MailOutbox;
import com.hankkiatti.domain.mail.entity.MailType;
import com.hankkiatti.domain.mail.repository.MailOutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mail.MailSendException;

@ExtendWith(MockitoExtension.class)
class MailRelayTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 12, 0);

    @Mock
    private MailOutboxRepository mailOutboxRepository;

    @Mock
    private MailOutboxRecorder mailOutboxRecorder;

    @Mock
    private SmtpMailClient smtpMailClient;

    private MailRelay mailRelay;

    @BeforeEach
    void setUp() {
        MailProperties properties = new MailProperties("no-reply@test", "한끼아띠", "http://localhost:3000",
                new MailProperties.Outbox(20, List.of(Duration.ofMinutes(1)), Duration.ofMinutes(5)));
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        mailRelay = new MailRelay(mailOutboxRepository, mailOutboxRecorder, smtpMailClient, properties, clock);
    }

    private MailOutbox mail() {
        return new MailOutbox(MailType.MATCHED, "helper@mju.ac.kr", "매칭 완료", "본문", 1L, NOW);
    }

    @Test
    void dispatch_선점성공_발송하고완료기록() {
        // given
        given(smtpMailClient.isConfigured()).willReturn(true);
        given(mailOutboxRepository.claim(1L, NOW, NOW.minusMinutes(5))).willReturn(true);
        given(mailOutboxRepository.findById(1L)).willReturn(Optional.of(mail()));

        // when
        mailRelay.dispatch(1L);

        // then
        verify(smtpMailClient).send("helper@mju.ac.kr", "매칭 완료", "본문");
        verify(mailOutboxRecorder).markSent(1L, NOW);
    }

    @Test
    void dispatch_다른곳이이미선점_보내지않음() {
        // given
        given(smtpMailClient.isConfigured()).willReturn(true);
        given(mailOutboxRepository.claim(anyLong(), any(), any())).willReturn(false);

        // when
        mailRelay.dispatch(1L);

        // then
        verify(smtpMailClient, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void dispatch_SMTP실패_실패기록() {
        // given
        given(smtpMailClient.isConfigured()).willReturn(true);
        given(mailOutboxRepository.claim(anyLong(), any(), any())).willReturn(true);
        given(mailOutboxRepository.findById(1L)).willReturn(Optional.of(mail()));
        willThrow(new MailSendException("connection refused"))
                .given(smtpMailClient).send(anyString(), anyString(), anyString());

        // when
        mailRelay.dispatch(1L);

        // then
        verify(mailOutboxRecorder).markFailed(1L, "connection refused", NOW);
        verify(mailOutboxRecorder, never()).markSent(anyLong(), any());
    }

    @Test
    void dispatch_SMTP설정없음_선점하지않고대기유지() {
        // given
        given(smtpMailClient.isConfigured()).willReturn(false);

        // when
        mailRelay.dispatch(1L);
        List<Long> dueIds = mailRelay.findDueIds();

        // then
        assertThat(dueIds).isEmpty();
        verifyNoInteractions(mailOutboxRepository);
    }

    @Test
    void findDueIds_보낼때가된메일을배치크기만큼찾는다() {
        // given
        given(smtpMailClient.isConfigured()).willReturn(true);
        given(mailOutboxRepository.findDispatchableIds(eq(NOW), eq(NOW.minusMinutes(5)), anyInt()))
                .willReturn(List.of(1L, 2L));

        // when
        List<Long> dueIds = mailRelay.findDueIds();

        // then
        assertThat(dueIds).containsExactly(1L, 2L);
        verify(mailOutboxRepository).findDispatchableIds(NOW, NOW.minusMinutes(5), 20);
        verify(mailOutboxRepository, never()).claim(anyLong(), any(), any());
    }

    @Test
    void dispatchSafely_완료기록에서예외_삼키고다음건은보낸다() {
        // given — 1번은 보낸 뒤 완료 기록에서 DB 예외
        given(smtpMailClient.isConfigured()).willReturn(true);
        given(mailOutboxRepository.claim(anyLong(), eq(NOW), eq(NOW.minusMinutes(5)))).willReturn(true);
        given(mailOutboxRepository.findById(anyLong())).willReturn(Optional.of(mail()));
        willThrow(new DataAccessResourceFailureException("db down")).willDoNothing()
                .given(mailOutboxRecorder).markSent(anyLong(), eq(NOW));

        // when
        mailRelay.dispatchSafely(1L);
        mailRelay.dispatchSafely(2L);

        // then
        verify(mailOutboxRecorder).markSent(2L, NOW);
        verify(mailOutboxRecorder, never()).markFailed(anyLong(), any(), any());
    }
}
