package com.hankkiatti.domain.application.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.application.dto.response.ApplyResponseDto;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.exception.ApplicationErrorType;
import com.hankkiatti.domain.application.exception.ApplicationException;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.application.service.ApplicationService;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
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
 * 실제 트랜잭션·락으로 동시 지원을 확인한다 (요구사항 4.2 "동시 지원은 서버 도착 순"). 테스트마다 커밋하므로 끝나면 지운다.
 */
@SpringBootTest
class ApplicationConcurrencyTest {

    private static final int HELPER_COUNT = 5;

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
    private final List<Long> helperIds = new ArrayList<>();
    private Student student;
    private LocalDateTime tomorrowNoon;

    @BeforeEach
    void setUp() {
        Account studentAccount = accountRepository.save(new Account("60239997", "hash", AccountRole.STUDENT, false, false));
        accountIds.add(studentAccount.getId());
        student = studentRepository.save(TestProfiles.student(studentAccount));
        for (int i = 0; i < HELPER_COUNT; i++) {
            Account account = accountRepository.save(
                    new Account("concurrency-helper" + i + "@mju.ac.kr", "hash", AccountRole.HELPER, false, false));
            accountIds.add(account.getId());
            helperIds.add(helperRepository.save(TestProfiles.helper(account, "6029000" + i)).getAccountId());
        }
        tomorrowNoon = LocalDateTime.now(clock).plusDays(1).truncatedTo(ChronoUnit.DAYS).withHour(12);
    }

    // 다른 테스트 데이터를 건드리지 않게 이 테스트가 만든 것만 지운다. 지원·신청은 커밋하는 다른 테스트가 없어 전부 지운다
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

    private HelpRequest saveRequest(LocalDateTime startAt) {
        return helpRequestRepository.save(new HelpRequest(student, startAt, Set.of(HelpType.SERVING), null, null));
    }

    /**
     * 작업들을 여러 스레드에서 한꺼번에 시작하고, 스레드별 결과(성공한 응답 또는 예외)를 돌려준다.
     */
    private List<Object> runConcurrently(List<Callable<ApplyResponseDto>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<ApplyResponseDto> task : tasks) {
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

    private static List<ApplyResponseDto> successes(List<Object> results) {
        return results.stream().filter(ApplyResponseDto.class::isInstance).map(ApplyResponseDto.class::cast).toList();
    }

    private static List<Object> failures(List<Object> results) {
        return results.stream().filter(result -> !(result instanceof ApplyResponseDto)).toList();
    }

    @Test
    void apply_모집중신청에도우미5명동시지원_한명만매칭되고나머지예비1부터4번() throws Exception {
        // given
        Long requestId = saveRequest(tomorrowNoon).getId();
        List<Callable<ApplyResponseDto>> tasks = helperIds.stream()
                .<Callable<ApplyResponseDto>>map(helperId -> () -> applicationService.apply(helperId, requestId))
                .toList();

        // when
        List<Object> results = runConcurrently(tasks);

        // then
        assertThat(failures(results)).isEmpty();
        assertThat(successes(results)).filteredOn(result -> result.status() == ApplicationStatus.MATCHED).hasSize(1);
        assertThat(successes(results)).filteredOn(result -> result.status() == ApplicationStatus.WAITING)
                .extracting(ApplyResponseDto::waitingOrder)
                .containsExactlyInAnyOrder(1, 2, 3, 4);
        assertThat(helpRequestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(HelpRequestStatus.MATCHED);
        assertThat(applicationRepository.findByHelpRequestIdAndStatus(requestId, ApplicationStatus.MATCHED)).hasSize(1);
    }

    @Test
    void apply_한도우미가겹치는두신청에동시지원_하나만매칭되고하나는TIME_OVERLAP() throws Exception {
        // given
        Long noon = saveRequest(tomorrowNoon).getId();
        Long halfPast = saveRequest(tomorrowNoon.plusMinutes(30)).getId();
        Long helperId = helperIds.get(0);

        // when
        List<Object> results = runConcurrently(List.of(
                () -> applicationService.apply(helperId, noon),
                () -> applicationService.apply(helperId, halfPast)));

        // then
        assertThat(successes(results)).singleElement()
                .extracting(ApplyResponseDto::status).isEqualTo(ApplicationStatus.MATCHED);
        assertThat(failures(results)).singleElement()
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCode").isEqualTo(ApplicationErrorType.TIME_OVERLAP);
        assertThat(applicationRepository.count()).isEqualTo(1);
    }

    @Test
    void apply_같은신청을동시에세번_한건만생기고나머지ALREADY_APPLIED() throws Exception {
        // given — 버튼을 여러 번 누른 경우
        Long requestId = saveRequest(tomorrowNoon).getId();
        Long helperId = helperIds.get(0);
        Callable<ApplyResponseDto> apply = () -> applicationService.apply(helperId, requestId);

        // when
        List<Object> results = runConcurrently(List.of(apply, apply, apply));

        // then
        assertThat(successes(results)).hasSize(1);
        assertThat(failures(results)).hasSize(2).allSatisfy(error -> assertThat(error)
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCode").isEqualTo(ApplicationErrorType.ALREADY_APPLIED));
        assertThat(applicationRepository.count()).isEqualTo(1);
    }
}
