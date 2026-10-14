package com.hankkiatti.domain.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MailOutboxPollerTest {

    @Mock
    private MailRelay mailRelay;

    // 넘겨받은 작업을 바로 실행하지 않고 쌓아 둔다 — 스케줄러 스레드에서 발송하지 않는지 보려고
    private final List<Runnable> submitted = new ArrayList<>();
    private final Executor mailExecutor = submitted::add;

    private MailOutboxPoller poller;

    @BeforeEach
    void setUp() {
        poller = new MailOutboxPoller(mailRelay, mailExecutor);
    }

    @Test
    void poll_보낼때가된메일_한통씩전용스레드풀에넘긴다() {
        // given
        given(mailRelay.findDueIds()).willReturn(List.of(1L, 2L));

        // when
        poller.poll();

        // then — 폴러 스레드에서는 보내지 않고, 풀에서 실행될 때 보낸다
        assertThat(submitted).hasSize(2);
        verify(mailRelay, never()).dispatchSafely(1L);
        submitted.forEach(Runnable::run);
        verify(mailRelay).dispatchSafely(1L);
        verify(mailRelay).dispatchSafely(2L);
    }

    @Test
    void poll_보낼메일없음_아무것도넘기지않는다() {
        // given
        given(mailRelay.findDueIds()).willReturn(List.of());

        // when
        poller.poll();

        // then
        assertThat(submitted).isEmpty();
    }
}
