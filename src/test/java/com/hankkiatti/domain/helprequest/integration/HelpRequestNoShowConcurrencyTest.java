package com.hankkiatti.domain.helprequest.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.exception.HelpRequestErrorType;
import com.hankkiatti.domain.helprequest.exception.HelpRequestException;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.helprequest.service.HelpRequestService;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.support.TestProfiles;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

/**
 * 같은 신청을 동시에 두 번 노쇼 신고했을 때 신청 행 락으로 한 번만 처리되는지 확인한다. 테스트마다 커밋하므로 끝나면 지운다.
 */
@SpringBootTest
class HelpRequestNoShowConcurrencyTest {

    private static final int THREADS = 2;

    @Autowired
    private HelpRequestService helpRequestService;

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

    private Long studentId;
    private Long helperId;
    private Long helpRequestId;
    private Long applicationId;

    @BeforeEach
    void setUp() {
        Account studentAccount = accountRepository.save(
                new Account("60239996", "hash", AccountRole.STUDENT, false, false));
        Student student = studentRepository.save(TestProfiles.student(studentAccount));
        Account helperAccount = accountRepository.save(
                new Account("noshow-helper@mju.ac.kr", "hash", AccountRole.HELPER, false, false));
        Helper helper = helperRepository.save(TestProfiles.helper(helperAccount, "60239995"));
        studentId = studentAccount.getId();
        helperId = helperAccount.getId();

        LocalDateTime startAt = LocalDateTime.now(clock).minusHours(3);
        HelpRequest request = new HelpRequest(student, startAt, Set.of(HelpType.SERVING), null, null);
        request.match(startAt.minusDays(1));
        request.complete(startAt.plusHours(1));
        helpRequestId = helpRequestRepository.save(request).getId();
        Application application = new Application(request, helper, startAt.minusDays(1));
        application.match(startAt.minusDays(1));
        application.complete();
        applicationId = applicationRepository.save(application).getId();
    }

    // 다른 테스트 데이터를 건드리지 않게 이 테스트가 만든 것만 지운다
    @AfterEach
    void tearDown() {
        applicationRepository.deleteById(applicationId);
        helpRequestRepository.deleteById(helpRequestId);
        helperRepository.deleteById(helperId);
        studentRepository.deleteById(studentId);
        accountRepository.deleteById(helperId);
        accountRepository.deleteById(studentId);
    }

    @Test
    void reportNoShow_같은신청동시에두번_한번만성공() throws Exception {
        // given
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();

        // when
        try {
            for (int i = 0; i < THREADS; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        helpRequestService.reportNoShow(studentId, helpRequestId);
                        return null;
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            ready.await();
            start.countDown();
        } finally {
            executor.shutdown();
        }
        List<Throwable> results = new ArrayList<>();
        for (Future<Throwable> future : futures) {
            results.add(future.get());
        }

        // then
        assertThat(results).filteredOn(result -> result == null).hasSize(1);
        assertThat(results).filteredOn(result -> result != null).singleElement()
                .isInstanceOf(HelpRequestException.class)
                .extracting("errorCode").isEqualTo(HelpRequestErrorType.INVALID_STATUS);
    }
}
