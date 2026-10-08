package com.hankkiatti.domain.sms.service;

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

import com.hankkiatti.domain.sms.entity.SmsOutbox;
import com.hankkiatti.domain.sms.entity.SmsType;
import com.hankkiatti.domain.sms.repository.SmsOutboxRepository;
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

@ExtendWith(MockitoExtension.class)
class SmsRelayTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 12, 0);

    @Mock
    private SmsOutboxRepository smsOutboxRepository;

    @Mock
    private SmsOutboxRecorder smsOutboxRecorder;

    @Mock
    private SmsSender smsSender;

    private SmsRelay smsRelay;

    @BeforeEach
    void setUp() {
        SmsProperties properties = new SmsProperties(new SmsProperties.Sns(true, "ap-northeast-2"),
                new SmsProperties.Outbox(20, List.of(Duration.ofMinutes(1)), Duration.ofMinutes(5)));
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        smsRelay = new SmsRelay(smsOutboxRepository, smsOutboxRecorder, smsSender, properties, clock);
    }

    private SmsOutbox sms() {
        return new SmsOutbox(SmsType.MATCHED, "+821012345678", "본문", 1L, NOW);
    }

    @Test
    void dispatch_선점성공_발송하고완료기록() {
        // given
        given(smsSender.isConfigured()).willReturn(true);
        given(smsOutboxRepository.claim(1L, NOW, NOW.minusMinutes(5))).willReturn(true);
        given(smsOutboxRepository.findById(1L)).willReturn(Optional.of(sms()));

        // when
        smsRelay.dispatch(1L);

        // then
        verify(smsSender).send("+821012345678", "본문");
        verify(smsOutboxRecorder).markSent(1L, NOW);
    }

    @Test
    void dispatch_다른곳이이미선점_보내지않음() {
        // given
        given(smsSender.isConfigured()).willReturn(true);
        given(smsOutboxRepository.claim(anyLong(), any(), any())).willReturn(false);

        // when
        smsRelay.dispatch(1L);

        // then
        verify(smsSender, never()).send(anyString(), anyString());
    }

    @Test
    void dispatch_발송실패_실패기록() {
        // given
        given(smsSender.isConfigured()).willReturn(true);
        given(smsOutboxRepository.claim(anyLong(), any(), any())).willReturn(true);
        given(smsOutboxRepository.findById(1L)).willReturn(Optional.of(sms()));
        willThrow(new SmsSendException("SNS 발송 실패: throttled", null))
                .given(smsSender).send(anyString(), anyString());

        // when
        smsRelay.dispatch(1L);

        // then
        verify(smsOutboxRecorder).markFailed(1L, "SNS 발송 실패: throttled", NOW);
        verify(smsOutboxRecorder, never()).markSent(anyLong(), any());
    }

    @Test
    void dispatch_발송설정없음_선점하지않고대기유지() {
        // given
        given(smsSender.isConfigured()).willReturn(false);

        // when
        smsRelay.dispatch(1L);
        smsRelay.dispatchDue();

        // then
        verifyNoInteractions(smsOutboxRepository);
    }

    @Test
    void dispatchDue_보낼때가된문자를배치크기만큼처리() {
        // given
        given(smsSender.isConfigured()).willReturn(true);
        given(smsOutboxRepository.findDispatchableIds(eq(NOW), eq(NOW.minusMinutes(5)), anyInt()))
                .willReturn(List.of(1L, 2L));

        // when
        smsRelay.dispatchDue();

        // then
        verify(smsOutboxRepository).findDispatchableIds(NOW, NOW.minusMinutes(5), 20);
        verify(smsOutboxRepository).claim(1L, NOW, NOW.minusMinutes(5));
        verify(smsOutboxRepository).claim(2L, NOW, NOW.minusMinutes(5));
    }
}
