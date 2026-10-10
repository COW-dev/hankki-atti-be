package com.hankkiatti.domain.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.BDDMockito.willThrow;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.event.HelpRequestFailedEvent;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.mail.repository.MailOutboxRepository;
import com.hankkiatti.domain.notification.entity.NotificationJob;
import com.hankkiatti.domain.notification.entity.NotificationJobStatus;
import com.hankkiatti.domain.notification.entity.NotificationJobType;
import com.hankkiatti.domain.notification.entity.NotificationTargetType;
import com.hankkiatti.domain.notification.entity.NotificationType;
import com.hankkiatti.domain.notification.repository.NotificationJobRepository;
import com.hankkiatti.domain.notification.repository.NotificationRepository;
import com.hankkiatti.domain.notification.service.NotificationJobRelay;
import com.hankkiatti.domain.notification.service.NotificationService;
import com.hankkiatti.domain.sms.repository.SmsOutboxRepository;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.support.TestProfiles;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 작업 아웃박스가 알림을 놓치지 않는지 실제 커밋으로 확인한다: 업무가 롤백되면 작업도 없고, 처리에 실패해도 작업이 남아
 * 다시 처리되며, 같은 작업을 두 곳이 처리해도 알림은 한 번이다. 커밋하므로 끝나면 지운다.
 */
@SpringBootTest
class NotificationJobIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private NotificationJobRelay notificationJobRelay;

    @Autowired
    private NotificationJobRepository notificationJobRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MailOutboxRepository mailOutboxRepository;

    @Autowired
    private SmsOutboxRepository smsOutboxRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private HelpRequestRepository helpRequestRepository;

    @Autowired
    private Clock clock;

    @MockitoSpyBean
    private NotificationService notificationService;

    private Long accountId;
    private Long studentId;
    private HelpRequest request;

    @BeforeEach
    void setUp() {
        Account account = accountRepository.save(new Account("60239991", "hash", AccountRole.STUDENT, false, false));
        accountId = account.getId();
        Student student = studentRepository.save(TestProfiles.student(account));
        studentId = student.getAccountId();
        request = helpRequestRepository.save(new HelpRequest(student,
                LocalDateTime.now(clock).plusDays(1).truncatedTo(ChronoUnit.DAYS).withHour(12),
                Set.of(HelpType.SERVING), null, null));
    }

    @AfterEach
    void tearDown() {
        notificationJobRepository.deleteAll();
        notificationRepository.deleteAll();
        mailOutboxRepository.deleteAll();
        smsOutboxRepository.deleteAll();
        helpRequestRepository.deleteAll();
        studentRepository.deleteById(studentId);
        accountRepository.deleteById(accountId);
    }

    // 매칭 실패 이벤트를 발행하는 업무 트랜잭션 하나
    private void failRequestInTransaction(boolean rollback) {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new HelpRequestFailedEvent(request.getId(), studentId, request.getStartAt()));
            if (rollback) {
                status.setRollbackOnly();
            }
        });
    }

    private List<NotificationType> notificationTypes() {
        return notificationRepository.findPage(studentId, null, 10).stream().map(n -> n.getType()).toList();
    }

    private NotificationJob onlyJob() {
        List<NotificationJob> jobs = notificationJobRepository.findAll();
        assertThat(jobs).hasSize(1);
        return jobs.getFirst();
    }

    private void makeDueNow(NotificationJob job) {
        ReflectionTestUtils.setField(job, "nextAttemptAt", LocalDateTime.now(clock).minusSeconds(1));
        notificationJobRepository.save(job);
    }

    @Test
    void 업무가커밋되면_작업이남고바로처리된다() {
        // when
        failRequestInTransaction(false);

        // then
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            NotificationJob job = onlyJob();
            assertThat(job.getType()).isEqualTo(NotificationJobType.REQUEST_FAILED);
            assertThat(job.getStatus()).isEqualTo(NotificationJobStatus.DONE);
            assertThat(notificationTypes()).containsExactly(NotificationType.REQUEST_FAILED);
        });
    }

    @Test
    void 업무가롤백되면_작업도알림도없다() {
        // when
        failRequestInTransaction(true);

        // then
        assertThat(notificationJobRepository.count()).isZero();
        assertThat(notificationTypes()).isEmpty();
    }

    @Test
    void 처리에실패해도_작업이남고_다시처리하면알림이생긴다() {
        // given — 알림 저장이 한 번 실패한다
        willThrow(new DataAccessResourceFailureException("db down")).given(notificationService)
                .notify(anyLong(), any(NotificationType.class), anyString(), any(NotificationTargetType.class),
                        anyLong());

        // when
        failRequestInTransaction(false);

        // then — 업무는 끝났고, 작업은 1분 뒤 재시도로 남았다. 알림·메일은 함께 롤백돼 없다
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            NotificationJob failed = onlyJob();
            assertThat(failed.getStatus()).isEqualTo(NotificationJobStatus.PENDING);
            assertThat(failed.getAttempts()).isEqualTo(1);
            assertThat(failed.getLastError()).isEqualTo("db down");
            assertThat(notificationTypes()).isEmpty();
            assertThat(mailOutboxRepository.count()).isZero();
        });

        // when — 장애가 풀리고 재시도 시각이 되어 폴러가 돈다
        willCallRealMethod().given(notificationService)
                .notify(anyLong(), any(NotificationType.class), anyString(), any(NotificationTargetType.class),
                        anyLong());
        NotificationJob failed = onlyJob();
        makeDueNow(failed);
        notificationJobRelay.processDue();

        // then
        assertThat(onlyJob().getStatus()).isEqualTo(NotificationJobStatus.DONE);
        assertThat(notificationTypes()).containsExactly(NotificationType.REQUEST_FAILED);
        assertThat(mailOutboxRepository.count()).isEqualTo(1);
    }

    @Test
    void 처리중서버가죽은작업_선점만료뒤다시처리된다() {
        // given — 선점(PROCESSING)된 채 10분이 지났다
        NotificationJob job = notificationJobRepository.save(new NotificationJob(NotificationJobType.REQUEST_FAILED,
                request.getId(), null, LocalDateTime.now(clock).minusMinutes(10)));
        ReflectionTestUtils.setField(job, "status", NotificationJobStatus.PROCESSING);
        ReflectionTestUtils.setField(job, "claimedAt", LocalDateTime.now(clock).minusMinutes(10));
        notificationJobRepository.save(job);

        // when
        notificationJobRelay.processDue();

        // then
        assertThat(onlyJob().getStatus()).isEqualTo(NotificationJobStatus.DONE);
        assertThat(notificationTypes()).containsExactly(NotificationType.REQUEST_FAILED);
    }

    @Test
    void 같은작업을두곳이동시에처리해도_알림은한번() throws Exception {
        for (int round = 0; round < 5; round++) {
            // given — 커밋 직후 처리 전에 놓친 대기 작업 (폴러와 다른 서버가 동시에 집는 상황)
            Long jobId = notificationJobRepository.save(new NotificationJob(NotificationJobType.REQUEST_FAILED,
                    request.getId(), null, LocalDateTime.now(clock).minusSeconds(1))).getId();

            // when
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            try {
                List<Future<?>> futures = List.of(
                        executor.submit(() -> {
                            start.await();
                            notificationJobRelay.process(jobId);
                            return null;
                        }),
                        executor.submit(() -> {
                            start.await();
                            notificationJobRelay.process(jobId);
                            return null;
                        }));
                start.countDown();
                for (Future<?> future : futures) {
                    future.get();
                }
            } finally {
                executor.shutdown();
            }

            // then
            assertThat(notificationTypes()).as("round " + round).containsExactly(NotificationType.REQUEST_FAILED);
            notificationRepository.deleteAll();
            mailOutboxRepository.deleteAll();
            smsOutboxRepository.deleteAll();
        }
    }
}
