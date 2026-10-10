package com.hankkiatti.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.notification.repository.NotificationJobRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class NotificationJobRelayTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 15, 0);

    @Mock
    private NotificationJobRepository notificationJobRepository;

    @Mock
    private NotificationDispatcher dispatcher;

    private NotificationJobRelay relay;

    @BeforeEach
    void setUp() {
        NotificationJobProperties properties = new NotificationJobProperties(50, List.of(Duration.ofMinutes(1)),
                Duration.ofMinutes(5));
        relay = new NotificationJobRelay(notificationJobRepository, dispatcher, properties,
                Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));
    }

    @Test
    void process_선점하면처리() {
        // given
        given(notificationJobRepository.claim(1L, NOW, NOW.minusMinutes(5))).willReturn(true);

        // when
        relay.process(1L);

        // then
        verify(dispatcher).process(1L, NOW);
        verify(dispatcher, never()).markFailed(anyLong(), any(), any());
    }

    @Test
    void process_다른곳이이미선점_건너뜀() {
        // given
        given(notificationJobRepository.claim(1L, NOW, NOW.minusMinutes(5))).willReturn(false);

        // when
        relay.process(1L);

        // then
        verify(dispatcher, never()).process(anyLong(), any());
    }

    @Test
    void process_처리실패_예외를밖으로내지않고재시도예약() {
        // given
        given(notificationJobRepository.claim(1L, NOW, NOW.minusMinutes(5))).willReturn(true);
        willThrow(new DataAccessResourceFailureException("db down")).given(dispatcher).process(1L, NOW);

        // when & then — 커밋 직후 업무 요청으로 번지지 않는다
        assertThatCode(() -> relay.process(1L)).doesNotThrowAnyException();
        verify(dispatcher).markFailed(1L, "db down", NOW);
    }

    @Test
    void process_실패기록도실패_예외를밖으로내지않음() {
        // given — 선점 만료 뒤 폴러가 다시 처리한다
        given(notificationJobRepository.claim(1L, NOW, NOW.minusMinutes(5))).willReturn(true);
        willThrow(new DataAccessResourceFailureException("db down")).given(dispatcher).process(1L, NOW);
        willThrow(new DataAccessResourceFailureException("db down")).given(dispatcher)
                .markFailed(eq(1L), any(), any());

        // when & then
        assertThatCode(() -> relay.process(1L)).doesNotThrowAnyException();
    }

    @Test
    void processDue_처리할작업을차례로() {
        // given
        given(notificationJobRepository.findProcessableIds(NOW, NOW.minusMinutes(5), 50)).willReturn(List.of(1L, 2L));
        given(notificationJobRepository.claim(anyLong(), eq(NOW), eq(NOW.minusMinutes(5)))).willReturn(true);

        // when
        relay.processDue();

        // then
        verify(dispatcher).process(1L, NOW);
        verify(dispatcher).process(2L, NOW);
    }
}
