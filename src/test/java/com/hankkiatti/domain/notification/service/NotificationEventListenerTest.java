package com.hankkiatti.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.application.event.WaitingExcludedEvent;
import com.hankkiatti.domain.helprequest.event.HelpRequestFailedEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationDispatcher dispatcher;

    @InjectMocks
    private NotificationEventListener listener;

    @Test
    void 알림저장실패_예외를삼켜업무에번지지않음() {
        // given
        WaitingExcludedEvent event = new WaitingExcludedEvent(31L);
        willThrow(new DataAccessResourceFailureException("db down")).given(dispatcher).waitingExcluded(event);

        // when & then
        assertThatCode(() -> listener.onWaitingExcluded(event)).doesNotThrowAnyException();
        verify(dispatcher).waitingExcluded(event);
    }

    @Test
    void 이벤트를Dispatcher로넘김() {
        // given
        HelpRequestFailedEvent event = new HelpRequestFailedEvent(10L, 1L, LocalDateTime.of(2026, 10, 12, 12, 0));

        // when
        listener.onRequestFailed(event);

        // then
        verify(dispatcher).requestFailed(event);
    }
}
