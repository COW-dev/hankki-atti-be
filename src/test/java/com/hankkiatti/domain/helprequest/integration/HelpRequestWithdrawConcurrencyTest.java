package com.hankkiatti.domain.helprequest.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
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
 * 같은 신청을 동시에 두 번 철회했을 때 신청 행 락으로 한 번만 처리되는지 확인한다. 테스트마다 커밋하므로 끝나면 지운다.
 */
@SpringBootTest
class HelpRequestWithdrawConcurrencyTest {

    private static final String STUDENT_NO = "60239997";
    private static final int THREADS = 2;

    @Autowired
    private HelpRequestService helpRequestService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private HelpRequestRepository helpRequestRepository;

    @Autowired
    private Clock clock;

    private Long accountId;
    private Long helpRequestId;

    @BeforeEach
    void setUp() {
        Account account = accountRepository.save(new Account(STUDENT_NO, "hash", AccountRole.STUDENT, false, false));
        Student student = studentRepository.save(TestProfiles.student(account));
        accountId = account.getId();
        helpRequestId = helpRequestRepository.save(new HelpRequest(student, LocalDateTime.now(clock).plusDays(1),
                Set.of(HelpType.SERVING), null, null)).getId();
    }

    @AfterEach
    void tearDown() {
        helpRequestRepository.deleteById(helpRequestId);
        studentRepository.deleteById(accountId);
        accountRepository.deleteById(accountId);
    }

    @Test
    void withdraw_같은신청동시에두번_한번만성공() throws Exception {
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
                        helpRequestService.withdraw(accountId, helpRequestId);
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
