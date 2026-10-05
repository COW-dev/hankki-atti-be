package com.hankkiatti.domain.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.mail.entity.MailOutbox;
import com.hankkiatti.domain.mail.entity.MailOutboxStatus;
import com.hankkiatti.domain.mail.entity.MailType;
import com.hankkiatti.domain.mail.event.MailFailedEvent;
import com.hankkiatti.domain.mail.repository.MailOutboxRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 아웃박스 → 커밋 후 비동기 발송 흐름을 실제 트랜잭션·스레드 풀로 확인한다. SMTP만 목으로 바꾼다.
 */
@SpringBootTest
@RecordApplicationEvents
class MailOutboxIntegrationTest {

    @MockitoBean
    private JavaMailSender javaMailSender;

    @Autowired
    private MailOutboxService mailOutboxService;

    @Autowired
    private MailOutboxRepository mailOutboxRepository;

    @Autowired
    private MailOutboxRecorder mailOutboxRecorder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ApplicationEvents applicationEvents;

    @BeforeEach
    void setUp() {
        given(javaMailSender.createMimeMessage())
                .willAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @AfterEach
    void tearDown() {
        mailOutboxRepository.deleteAll();
    }

    private Long enqueueInTransaction() {
        return transactionTemplate.execute(status -> mailOutboxService.enqueue(
                MailType.STUDENT_CREDENTIAL, "60231234@mju.ac.kr", "[한끼아띠] 계정 안내", "초기 비밀번호: abc!123", 7L));
    }

    @Test
    void enqueue_커밋되면_메일스레드에서발송하고본문을지움() throws Exception {
        // when
        Long id = enqueueInTransaction();

        // then
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(mailOutboxRepository.findById(id).orElseThrow().getStatus()).isEqualTo(MailOutboxStatus.SENT));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(sent.capture());
        MimeMessage message = sent.getValue();
        assertThat(message.getSubject()).isEqualTo("[한끼아띠] 계정 안내");
        assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly("60231234@mju.ac.kr");
        assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("한끼아띠 (명지대학교 장애학생지원센터)");
        assertThat(message.getContent()).isEqualTo("초기 비밀번호: abc!123");
        assertThat(mailOutboxRepository.findById(id).orElseThrow().getBody()).isEmpty();
    }

    @Test
    void enqueue_업무가롤백되면_메일도남지않고보내지않음() {
        // when
        transactionTemplate.executeWithoutResult(status -> {
            mailOutboxService.enqueue(MailType.MATCHED, "helper@mju.ac.kr", "매칭 완료", "본문", 1L);
            status.setRollbackOnly();
        });

        // then
        assertThat(mailOutboxRepository.count()).isZero();
        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void 발송실패_재시도로다시예약() {
        // given
        willThrow(new MailSendException("connection refused")).given(javaMailSender).send(any(MimeMessage.class));

        // when
        Long id = enqueueInTransaction();

        // then
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(mailOutboxRepository.findById(id).orElseThrow().getAttempts()).isEqualTo(1));
        MailOutbox mail = mailOutboxRepository.findById(id).orElseThrow();
        assertThat(mail.getStatus()).isEqualTo(MailOutboxStatus.PENDING);
        assertThat(mail.getNextAttemptAt()).isAfter(LocalDateTime.now().plusSeconds(30));
        assertThat(mail.getLastError()).isEqualTo("connection refused");
        assertThat(mail.getBody()).isNotEmpty();
    }

    @Test
    void claim_같은메일을두번선점_한번만성공하고오래된선점은다시가능() {
        // given
        LocalDateTime now = LocalDateTime.now();
        MailOutbox mail = mailOutboxRepository.save(
                new MailOutbox(MailType.MATCHED, "helper@mju.ac.kr", "제목", "본문", 1L, now.minusSeconds(1)));

        // when & then
        assertThat(mailOutboxRepository.claim(mail.getId(), now, now.minusMinutes(5))).isTrue();
        assertThat(mailOutboxRepository.claim(mail.getId(), now, now.minusMinutes(5))).isFalse();
        // 선점 시각이 staleBefore보다 이전이면 발송 중 서버가 죽은 것으로 보고 다시 선점한다
        assertThat(mailOutboxRepository.claim(mail.getId(), now.plusMinutes(6), now.plusMinutes(1))).isTrue();
    }

    @Test
    void findDispatchableIds_발송시각된대기와오래된선점만_오래된순으로() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Long due = mailOutboxRepository.save(
                new MailOutbox(MailType.MATCHED, "a@mju.ac.kr", "제목", "본문", 1L, now.minusMinutes(1))).getId();
        mailOutboxRepository.save(new MailOutbox(MailType.MATCHED, "b@mju.ac.kr", "제목", "본문", 2L, now.plusMinutes(1)));
        Long stale = mailOutboxRepository.save(
                new MailOutbox(MailType.MATCHED, "c@mju.ac.kr", "제목", "본문", 3L, now.minusMinutes(20))).getId();
        mailOutboxRepository.claim(stale, now.minusMinutes(10), now.minusMinutes(15));
        Long fresh = mailOutboxRepository.save(
                new MailOutbox(MailType.MATCHED, "d@mju.ac.kr", "제목", "본문", 4L, now.minusMinutes(1))).getId();
        mailOutboxRepository.claim(fresh, now, now.minusMinutes(5));

        // when
        List<Long> ids = mailOutboxRepository.findDispatchableIds(now, now.minusMinutes(5), 20);

        // then — 아직 때가 안 된 메일과 방금 선점된 메일은 빠진다
        assertThat(ids).containsExactly(due, stale);
        assertThat(mailOutboxRepository.findDispatchableIds(now, now.minusMinutes(5), 1)).containsExactly(due);
    }

    @Test
    void markFailed_재시도를다쓰면_실패이벤트발행() {
        // given
        LocalDateTime now = LocalDateTime.now();
        MailOutbox mail = mailOutboxRepository.save(
                new MailOutbox(MailType.STUDENT_CREDENTIAL, "60231234@mju.ac.kr", "제목", "본문", 7L, now));

        // when — 기본 재시도 3회 + 마지막 실패
        for (int attempt = 0; attempt < 4; attempt++) {
            mailOutboxRecorder.markFailed(mail.getId(), "timeout", now);
        }

        // then
        assertThat(mailOutboxRepository.findById(mail.getId()).orElseThrow().isFailed()).isTrue();
        assertThat(applicationEvents.stream(MailFailedEvent.class))
                .containsExactly(new MailFailedEvent(mail.getId(), MailType.STUDENT_CREDENTIAL, 7L));
    }
}
