package com.hankkiatti.domain.application.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.application.dto.request.HelperCancelRequestDto;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.entity.CancelReason;
import com.hankkiatti.domain.application.exception.ApplicationErrorType;
import com.hankkiatti.domain.application.exception.ApplicationException;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.application.service.ApplicationService;
import com.hankkiatti.domain.application.service.HelperCancelService;
import com.hankkiatti.domain.application.service.PromotionResponseService;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.mail.repository.MailOutboxRepository;
import com.hankkiatti.domain.notification.repository.NotificationJobRepository;
import com.hankkiatti.domain.notification.repository.NotificationRepository;
import com.hankkiatti.domain.sms.repository.SmsOutboxRepository;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.support.TestProfiles;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 승격 응답(수락·거절·자동 거절)이 동시에 들어와도 한 번만 반영되는지, 수락이 커밋되면 겹치는 다른 예비가 제외되는지
 * 실제 트랜잭션·락·이벤트로 확인한다. 경합은 타이밍에 따라 드러나므로 여러 번 반복한다. 커밋하므로 끝나면 지운다.
 */
@SpringBootTest
class PromotionResponseConcurrencyTest {

    private static final int ROUNDS = 6;
    private static final HelperCancelRequestDto ILLNESS = new HelperCancelRequestDto(CancelReason.ILLNESS, null);

    @Autowired
    private PromotionResponseService promotionResponseService;

    @Autowired
    private HelperCancelService helperCancelService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private HelperRepository helperRepository;

    @Autowired
    private HelpRequestRepository helpRequestRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationJobRepository notificationJobRepository;

    @Autowired
    private MailOutboxRepository mailOutboxRepository;

    @Autowired
    private SmsOutboxRepository smsOutboxRepository;

    @Autowired
    private Clock clock;

    private final List<Long> accountIds = new ArrayList<>();
    // 0: 매칭됐다 취소, 1: 응답 대기로 승격, 2: 그다음 예비, 3: 다른 신청의 매칭
    private final List<Long> helperIds = new ArrayList<>();
    private Student student;

    @BeforeEach
    void setUp() {
        Account studentAccount = accountRepository.save(new Account("60239994", "hash", AccountRole.STUDENT, false, false));
        accountIds.add(studentAccount.getId());
        student = studentRepository.save(TestProfiles.student(studentAccount));
        for (int i = 0; i < 4; i++) {
            Account account = accountRepository.save(
                    new Account("promotion-helper" + i + "@mju.ac.kr", "hash", AccountRole.HELPER, false, false));
            accountIds.add(account.getId());
            helperIds.add(helperRepository.save(TestProfiles.helper(account, "6029000" + i)).getAccountId());
        }
    }

    @AfterEach
    void tearDown() {
        // 커밋된 지원·취소가 알림 작업·알림·메일·문자 아웃박스를 쌓으므로 함께 지운다
        notificationJobRepository.deleteAll();
        notificationRepository.deleteAll();
        mailOutboxRepository.deleteAll();
        smsOutboxRepository.deleteAll();
        clearRequests();
        helperIds.forEach(helperRepository::deleteById);
        studentRepository.deleteById(student.getAccountId());
        accountIds.forEach(accountRepository::deleteById);
    }

    private void clearRequests() {
        applicationRepository.deleteAll();
        helpRequestRepository.deleteAll();
    }

    private Long saveRequest(LocalDateTime startAt) {
        return helpRequestRepository.save(new HelpRequest(student, startAt, Set.of(HelpType.SERVING), null, null))
                .getId();
    }

    // 40분 뒤 식사. 도우미0 매칭·도우미1·2 예비에서 도우미0이 취소해 도우미1이 응답 대기(마감 = 식사 15분 전)가 된다
    private Pending pendingRequest() {
        LocalDateTime startAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES).plusMinutes(40);
        Long requestId = saveRequest(startAt);
        Long matched = applicationService.apply(helperIds.get(0), requestId).applicationId();
        Long first = applicationService.apply(helperIds.get(1), requestId).applicationId();
        Long second = applicationService.apply(helperIds.get(2), requestId).applicationId();
        helperCancelService.cancel(helperIds.get(0), matched, ILLNESS);
        return new Pending(requestId, startAt, first, second);
    }

    private record Pending(Long requestId, LocalDateTime startAt, Long first, Long second) {}

    private ApplicationStatus statusOf(Long applicationId) {
        return applicationRepository.findById(applicationId).orElseThrow().getStatus();
    }

    // 도우미1이 수락해 확정됐거나, 거절돼 도우미2가 응답 대기거나 — 둘 중 하나만
    private void assertExactlyOneOutcome(Pending pending) {
        ApplicationStatus first = statusOf(pending.first());
        ApplicationStatus second = statusOf(pending.second());
        if (first == ApplicationStatus.MATCHED) {
            assertThat(second).isEqualTo(ApplicationStatus.WAITING);
        } else {
            assertThat(first).isEqualTo(ApplicationStatus.PROMOTION_DECLINED);
            assertThat(second).isEqualTo(ApplicationStatus.PROMOTION_PENDING);
        }
    }

    private List<Object> runConcurrently(List<Callable<?>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            ready.await();
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void accept_수락과응답마감자동거절이동시에_한쪽만반영() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            // given
            Pending pending = pendingRequest();

            // when — 도우미1이 마감 직전에 수락하는 순간 스케줄러가 마감으로 보고 자동 거절
            List<Object> results = runConcurrently(List.of(
                    () -> promotionResponseService.accept(helperIds.get(1), pending.first()),
                    () -> {
                        promotionResponseService.expireUnanswered(pending.first(), pending.startAt().minusMinutes(15));
                        return null;
                    }));

            // then — 자동 거절이 먼저면 수락은 응답 대기가 아니라 409
            assertExactlyOneOutcome(pending);
            if (statusOf(pending.first()) == ApplicationStatus.PROMOTION_DECLINED) {
                assertThat(results.get(0)).isInstanceOf(ApplicationException.class)
                        .extracting("errorCode").isEqualTo(ApplicationErrorType.INVALID_STATUS);
            }
            clearRequests();
        }
    }

    @Test
    void accept_수락과거절을동시에_하나만성공() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            // given
            Pending pending = pendingRequest();

            // when
            List<Object> results = runConcurrently(List.of(
                    () -> promotionResponseService.accept(helperIds.get(1), pending.first()),
                    () -> promotionResponseService.decline(helperIds.get(1), pending.first())));

            // then
            assertExactlyOneOutcome(pending);
            assertThat(results).filteredOn(ApplicationException.class::isInstance).hasSize(1);
            clearRequests();
        }
    }

    @Test
    void accept_수락이커밋되면_겹치는다른예비가제외된다() {
        // given — 도우미1은 30분 겹치는 다른 신청(도우미3 매칭)에 먼저 예비로 들어가 있다
        // (응답 대기가 된 뒤에는 겹치는 지원이 막힌다)
        LocalDateTime startAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES).plusMinutes(40);
        Long other = saveRequest(startAt.plusMinutes(30));
        applicationService.apply(helperIds.get(3), other);
        Long otherWait = applicationService.apply(helperIds.get(1), other).applicationId();
        Pending pending = pendingRequest();
        // 응답 대기로 승격될 때는 제외하지 않는다 — 거절하면 그 예비가 남아야 해서
        assertThat(statusOf(pending.first())).isEqualTo(ApplicationStatus.PROMOTION_PENDING);
        assertThat(statusOf(otherWait)).isEqualTo(ApplicationStatus.WAITING);

        // when
        promotionResponseService.accept(helperIds.get(1), pending.first());

        // then
        assertThat(statusOf(pending.first())).isEqualTo(ApplicationStatus.MATCHED);
        assertThat(statusOf(otherWait)).isEqualTo(ApplicationStatus.EXCLUDED);
    }
}
