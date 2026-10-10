package com.hankkiatti.domain.helprequest.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.application.dto.request.HelperCancelRequestDto;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.entity.CancelReason;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.application.service.ApplicationService;
import com.hankkiatti.domain.application.service.HelperCancelService;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.helprequest.service.HelpRequestService;
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
 * 장애학생 매칭 취소가 도우미 취소·새 지원과 동시에 일어나도, 취소된 신청에 진행 중 지원이 남지 않는지 실제 트랜잭션·락으로 확인한다.
 * 경합은 타이밍에 따라 드러나므로 여러 번 반복한다. 커밋하므로 끝나면 지운다.
 */
@SpringBootTest
class HelpRequestCancelConcurrencyTest {

    private static final int ROUNDS = 6;
    private static final HelperCancelRequestDto ILLNESS = new HelperCancelRequestDto(CancelReason.ILLNESS, null);

    @Autowired
    private HelpRequestService helpRequestService;

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
    // 0: 매칭, 1: 예비, 2: 새로 지원
    private final List<Long> helperIds = new ArrayList<>();
    private Student student;
    private LocalDateTime tomorrowNoon;

    @BeforeEach
    void setUp() {
        Account studentAccount = accountRepository.save(new Account("60239993", "hash", AccountRole.STUDENT, false, false));
        accountIds.add(studentAccount.getId());
        student = studentRepository.save(TestProfiles.student(studentAccount));
        for (int i = 0; i < 3; i++) {
            Account account = accountRepository.save(
                    new Account("student-cancel-helper" + i + "@mju.ac.kr", "hash", AccountRole.HELPER, false, false));
            accountIds.add(account.getId());
            helperIds.add(helperRepository.save(TestProfiles.helper(account, "6026000" + i)).getAccountId());
        }
        tomorrowNoon = LocalDateTime.now(clock).plusDays(1).truncatedTo(ChronoUnit.DAYS).withHour(12);
    }

    @AfterEach
    void tearDown() {
        // 커밋된 지원·취소가 알림 작업·알림·메일·문자 아웃박스를 쌓으므로 함께 지운다
        notificationJobRepository.deleteAll();
        notificationRepository.deleteAll();
        mailOutboxRepository.deleteAll();
        smsOutboxRepository.deleteAll();
        applicationRepository.deleteAll();
        helpRequestRepository.deleteAll();
        helperIds.forEach(helperRepository::deleteById);
        studentRepository.deleteById(student.getAccountId());
        accountIds.forEach(accountRepository::deleteById);
    }

    // 도우미0 매칭, 도우미1 예비인 신청. 도우미0의 지원 ID를 돌려준다
    private Long[] matchedWithWaiting(LocalDateTime startAt) {
        Long requestId = helpRequestRepository.save(
                new HelpRequest(student, startAt, Set.of(HelpType.SERVING), null, null)).getId();
        Long matched = applicationService.apply(helperIds.get(0), requestId).applicationId();
        applicationService.apply(helperIds.get(1), requestId);
        return new Long[] {requestId, matched};
    }

    // 신청이 취소됐고 진행 중(매칭·응답 대기·예비) 지원이 하나도 남지 않았다
    private void assertCanceledWithNoActive(Long requestId) {
        assertThat(helpRequestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(HelpRequestStatus.CANCELED);
        assertThat(applicationRepository.findAll()).filteredOn(application -> isOn(application, requestId))
                .noneMatch(Application::isActive);
    }

    private static boolean isOn(Application application, Long requestId) {
        return application.getHelpRequest().getId().equals(requestId);
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
    void cancelMatched_학생취소와도우미취소가동시에_신청은취소되고진행중지원이남지않는다() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            // given
            Long[] ids = matchedWithWaiting(tomorrowNoon.plusDays(round));

            // when — 도우미 취소가 먼저면 예비가 승격된 뒤 학생 취소, 학생 취소가 먼저면 도우미 취소는 409
            List<Object> results = runConcurrently(List.of(
                    () -> helpRequestService.cancelMatched(student.getAccountId(), ids[0]),
                    () -> helperCancelService.cancel(helperIds.get(0), ids[1], ILLNESS)));

            // then
            assertThat(results.get(0)).isNotInstanceOf(Exception.class);
            assertCanceledWithNoActive(ids[0]);
            assertThat(applicationRepository.findByHelpRequestIdAndStatus(ids[0], ApplicationStatus.STUDENT_CANCELED))
                    .isNotEmpty();
        }
    }

    @Test
    void cancelMatched_학생취소와새지원이동시에_취소된신청에예비가남지않는다() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            // given
            Long[] ids = matchedWithWaiting(tomorrowNoon.plusDays(round));

            // when — 지원이 먼저면 예비로 들어갔다 학생 취소, 학생 취소가 먼저면 지원은 409
            List<Object> results = runConcurrently(List.of(
                    () -> helpRequestService.cancelMatched(student.getAccountId(), ids[0]),
                    () -> applicationService.apply(helperIds.get(2), ids[0])));

            // then
            assertThat(results.get(0)).isNotInstanceOf(Exception.class);
            assertCanceledWithNoActive(ids[0]);
        }
    }
}
