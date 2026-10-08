package com.hankkiatti.domain.sms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.sms.entity.SmsOutbox;
import com.hankkiatti.domain.sms.entity.SmsOutboxStatus;
import com.hankkiatti.domain.sms.entity.SmsType;
import com.hankkiatti.domain.sms.event.SmsFailedEvent;
import com.hankkiatti.domain.sms.repository.SmsOutboxRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 아웃박스 → 커밋 후 비동기 발송 흐름을 실제 트랜잭션·스레드 풀로 확인한다. 발송부(SmsSender)만 목으로 바꾼다.
 */
@SpringBootTest
@RecordApplicationEvents
class SmsOutboxIntegrationTest {

    @MockitoBean
    private SmsSender smsSender;

    @Autowired
    private SmsOutboxService smsOutboxService;

    @Autowired
    private SmsOutboxRepository smsOutboxRepository;

    @Autowired
    private SmsOutboxRecorder smsOutboxRecorder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ApplicationEvents applicationEvents;

    @BeforeEach
    void setUp() {
        given(smsSender.isConfigured()).willReturn(true);
    }

    @AfterEach
    void tearDown() {
        smsOutboxRepository.deleteAll();
    }

    private Long enqueueInTransaction() {
        return transactionTemplate.execute(status -> smsOutboxService.enqueue(
                SmsType.MATCHED, "010-1234-5678", "[한끼아띠] 10/12 12:00 도우미가 정해졌어요", 7L));
    }

    @Test
    void enqueue_커밋되면_문자스레드에서E164번호로발송하고본문을지움() {
        // when
        Long id = enqueueInTransaction();

        // then
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(smsOutboxRepository.findById(id).orElseThrow().getStatus()).isEqualTo(SmsOutboxStatus.SENT));
        verify(smsSender).send("+821012345678", "[한끼아띠] 10/12 12:00 도우미가 정해졌어요");
        assertThat(smsOutboxRepository.findById(id).orElseThrow().getBody()).isEmpty();
    }

    @Test
    void enqueue_업무가롤백되면_문자도남지않고보내지않음() {
        // when
        transactionTemplate.executeWithoutResult(status -> {
            smsOutboxService.enqueue(SmsType.MATCHED, "010-1234-5678", "본문", 1L);
            status.setRollbackOnly();
        });

        // then
        assertThat(smsOutboxRepository.count()).isZero();
        verify(smsSender, never()).send(anyString(), anyString());
    }

    @Test
    void 발송실패_재시도로다시예약() {
        // given
        willThrow(new SmsSendException("SNS 발송 실패: throttled", null)).given(smsSender).send(anyString(), anyString());

        // when
        Long id = enqueueInTransaction();

        // then
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(smsOutboxRepository.findById(id).orElseThrow().getAttempts()).isEqualTo(1));
        SmsOutbox sms = smsOutboxRepository.findById(id).orElseThrow();
        assertThat(sms.getStatus()).isEqualTo(SmsOutboxStatus.PENDING);
        assertThat(sms.getNextAttemptAt()).isAfter(LocalDateTime.now().plusSeconds(30));
        assertThat(sms.getLastError()).isEqualTo("SNS 발송 실패: throttled");
        assertThat(sms.getBody()).isNotEmpty();
    }

    @Test
    void claim_같은문자를두번선점_한번만성공하고오래된선점은다시가능() {
        // given
        LocalDateTime now = LocalDateTime.now();
        SmsOutbox sms = smsOutboxRepository.save(
                new SmsOutbox(SmsType.MATCHED, "+821012345678", "본문", 1L, now.minusSeconds(1)));

        // when & then
        assertThat(smsOutboxRepository.claim(sms.getId(), now, now.minusMinutes(5))).isTrue();
        assertThat(smsOutboxRepository.claim(sms.getId(), now, now.minusMinutes(5))).isFalse();
        assertThat(smsOutboxRepository.claim(sms.getId(), now.plusMinutes(6), now.plusMinutes(1))).isTrue();
    }

    @Test
    void findDispatchableIds_발송시각된대기와오래된선점만() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Long due = smsOutboxRepository.save(
                new SmsOutbox(SmsType.MATCHED, "+821000000001", "본문", 1L, now.minusMinutes(1))).getId();
        smsOutboxRepository.save(new SmsOutbox(SmsType.MATCHED, "+821000000002", "본문", 2L, now.plusMinutes(1)));
        Long stale = smsOutboxRepository.save(
                new SmsOutbox(SmsType.MATCHED, "+821000000003", "본문", 3L, now.minusMinutes(20))).getId();
        smsOutboxRepository.claim(stale, now.minusMinutes(10), now.minusMinutes(15));

        // when
        List<Long> ids = smsOutboxRepository.findDispatchableIds(now, now.minusMinutes(5), 20);

        // then
        assertThat(ids).containsExactly(due, stale);
    }

    @Test
    void markFailed_재시도를다쓰면_실패이벤트발행() {
        // given
        LocalDateTime now = LocalDateTime.now();
        SmsOutbox sms = smsOutboxRepository.save(new SmsOutbox(SmsType.MATCH_FAILED, "+821012345678", "본문", 7L, now));

        // when — 기본 재시도 3회 + 마지막 실패
        for (int attempt = 0; attempt < 4; attempt++) {
            smsOutboxRecorder.markFailed(sms.getId(), "throttled", now);
        }

        // then
        assertThat(smsOutboxRepository.findById(sms.getId()).orElseThrow().isFailed()).isTrue();
        assertThat(applicationEvents.stream(SmsFailedEvent.class))
                .containsExactly(new SmsFailedEvent(sms.getId(), SmsType.MATCH_FAILED, 7L));
    }
}
