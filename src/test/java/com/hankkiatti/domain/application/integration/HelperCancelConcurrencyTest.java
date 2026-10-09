package com.hankkiatti.domain.application.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.application.dto.request.HelperCancelRequestDto;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.entity.CancelReason;
import com.hankkiatti.domain.application.exception.ApplicationErrorType;
import com.hankkiatti.domain.application.exception.ApplicationException;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.application.service.ApplicationService;
import com.hankkiatti.domain.application.service.HelperCancelService;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
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
 * 도우미 취소·승격이 지원과 동시에 일어나도 매칭이 꼬이지 않는지 실제 트랜잭션·락으로 확인한다.
 * 경합은 타이밍에 따라 드러나므로 시나리오마다 여러 번 반복한다. 테스트마다 커밋하므로 끝나면 지운다.
 */
@SpringBootTest
class HelperCancelConcurrencyTest {

    private static final int ROUNDS = 8;
    private static final HelperCancelRequestDto ILLNESS = new HelperCancelRequestDto(CancelReason.ILLNESS, null);

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
    private Clock clock;

    private final List<Long> accountIds = new ArrayList<>();
    private final List<Long> helperIds = new ArrayList<>();
    private Student student;
    private LocalDateTime tomorrowNoon;

    @BeforeEach
    void setUp() {
        Account studentAccount = accountRepository.save(new Account("60239996", "hash", AccountRole.STUDENT, false, false));
        accountIds.add(studentAccount.getId());
        student = studentRepository.save(TestProfiles.student(studentAccount));
        for (int i = 0; i < 3; i++) {
            Account account = accountRepository.save(
                    new Account("cancel-helper" + i + "@mju.ac.kr", "hash", AccountRole.HELPER, false, false));
            accountIds.add(account.getId());
            helperIds.add(helperRepository.save(TestProfiles.helper(account, "6028000" + i)).getAccountId());
        }
        tomorrowNoon = LocalDateTime.now(clock).plusDays(1).truncatedTo(ChronoUnit.DAYS).withHour(12);
    }

    @AfterEach
    void tearDown() {
        applicationRepository.deleteAll();
        helpRequestRepository.deleteAll();
        helperIds.forEach(helperRepository::deleteById);
        studentRepository.deleteById(student.getAccountId());
        accountIds.forEach(accountRepository::deleteById);
    }

    private Long saveRequest(LocalDateTime startAt) {
        return helpRequestRepository.save(new HelpRequest(student, startAt, Set.of(HelpType.SERVING), null, null))
                .getId();
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

    private List<Application> applicationsOf(Long helpRequestId, ApplicationStatus status) {
        return applicationRepository.findByHelpRequestIdAndStatus(helpRequestId, status);
    }

    @Test
    void cancel_매칭취소와새지원이동시에_매칭은늘한명이고예비가남은채모집중이되지않는다() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            // given — 도우미0이 매칭돼 있다. 도우미1이 지원하는 순간 도우미0이 취소한다
            Long requestId = saveRequest(tomorrowNoon.plusDays(round));
            Long mine = applicationService.apply(helperIds.get(0), requestId).applicationId();

            // when
            List<Object> results = runConcurrently(List.of(
                    () -> helperCancelService.cancel(helperIds.get(0), mine, ILLNESS),
                    () -> applicationService.apply(helperIds.get(1), requestId)));

            // then — 지원이 먼저면 예비로 들어갔다 승격, 취소가 먼저면 모집 재개 뒤 바로 매칭. 어느 쪽이든 결과는 같다
            assertThat(results).noneMatch(Exception.class::isInstance);
            assertThat(helpRequestRepository.findById(requestId).orElseThrow().getStatus())
                    .isEqualTo(HelpRequestStatus.MATCHED);
            assertThat(applicationsOf(requestId, ApplicationStatus.MATCHED)).singleElement()
                    .extracting(application -> application.getHelper().getAccountId()).isEqualTo(helperIds.get(1));
            assertThat(applicationsOf(requestId, ApplicationStatus.WAITING)).isEmpty();
        }
    }

    @Test
    void cancel_승격되는도우미가겹치는다른신청에동시에지원_확정매칭이겹치지않는다() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            // given — 도우미0 매칭·도우미1 예비인 12:00 신청, 30분 겹치는 12:30 모집 중 신청
            LocalDateTime noon = tomorrowNoon.plusDays(round);
            Long requestId = saveRequest(noon);
            Long overlapping = saveRequest(noon.plusMinutes(30));
            Long mine = applicationService.apply(helperIds.get(0), requestId).applicationId();
            applicationService.apply(helperIds.get(1), requestId);

            // when — 도우미0 취소(도우미1 승격)와 도우미1의 12:30 지원이 동시에
            runConcurrently(List.of(
                    () -> helperCancelService.cancel(helperIds.get(0), mine, ILLNESS),
                    () -> applicationService.apply(helperIds.get(1), overlapping)));

            // then — 도우미1은 둘 중 하나에만 확정 매칭
            long confirmed = applicationsOf(requestId, ApplicationStatus.MATCHED).stream()
                    .filter(application -> application.getHelper().getAccountId().equals(helperIds.get(1))).count()
                    + applicationsOf(overlapping, ApplicationStatus.MATCHED).stream()
                    .filter(application -> application.getHelper().getAccountId().equals(helperIds.get(1))).count();
            assertThat(confirmed).isEqualTo(1);
        }
    }

    @Test
    void cancel_같은취소를동시에두번_한번만처리되고예비는한명만승격() throws Exception {
        // given — 도우미0 매칭, 도우미1·2 예비
        Long requestId = saveRequest(tomorrowNoon);
        Long mine = applicationService.apply(helperIds.get(0), requestId).applicationId();
        applicationService.apply(helperIds.get(1), requestId);
        applicationService.apply(helperIds.get(2), requestId);
        Callable<?> cancel = () -> helperCancelService.cancel(helperIds.get(0), mine, ILLNESS);

        // when
        List<Object> results = runConcurrently(List.of(cancel, cancel));

        // then
        assertThat(results).filteredOn(Exception.class::isInstance).singleElement()
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCode").isEqualTo(ApplicationErrorType.INVALID_STATUS);
        assertThat(applicationsOf(requestId, ApplicationStatus.MATCHED)).singleElement()
                .extracting(application -> application.getHelper().getAccountId()).isEqualTo(helperIds.get(1));
        assertThat(applicationsOf(requestId, ApplicationStatus.WAITING)).singleElement()
                .extracting(application -> application.getHelper().getAccountId()).isEqualTo(helperIds.get(2));
    }
}
