package com.hankkiatti.domain.helprequest.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.helprequest.dto.request.HelpRequestCreateRequestDto;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.exception.HelpRequestErrorType;
import com.hankkiatti.domain.helprequest.exception.HelpRequestException;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.helprequest.service.HelpRequestSchedule;
import com.hankkiatti.domain.helprequest.service.HelpRequestService;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.support.TestProfiles;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
 * 같은 장애학생이 겹치는 신청을 동시에 보냈을 때 실제 트랜잭션·락으로 한 건만 생기는지 확인한다. 테스트마다 커밋하므로 끝나면 지운다.
 */
@SpringBootTest
class HelpRequestCreateConcurrencyTest {

    private static final String STUDENT_NO = "60239998";

    @Autowired
    private HelpRequestService helpRequestService;

    @Autowired
    private HelpRequestSchedule helpRequestSchedule;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private HelpRequestRepository helpRequestRepository;

    @Autowired
    private Clock clock;

    private Long accountId;
    private LocalDateTime bookableNoon;

    @BeforeEach
    void setUp() {
        Account account = accountRepository.save(new Account(STUDENT_NO, "hash", AccountRole.STUDENT, false, false));
        studentRepository.save(TestProfiles.student(account));
        accountId = account.getId();

        LocalDate today = LocalDate.now(clock);
        bookableNoon = helpRequestSchedule.bookableStartTimes(LocalDateTime.now(clock)).stream()
                .filter(startAt -> startAt.toLocalDate().isAfter(today) && startAt.toLocalTime().equals(LocalTime.NOON))
                .findFirst()
                .orElseThrow();
    }

    // 다른 테스트 데이터를 건드리지 않게 이 테스트가 만든 계정만 지운다. 신청은 커밋하는 다른 테스트가 없어 전부 지운다
    @AfterEach
    void tearDown() {
        helpRequestRepository.deleteAll();
        studentRepository.deleteById(accountId);
        accountRepository.deleteById(accountId);
    }

    /**
     * 신청들을 여러 스레드에서 한꺼번에 시작하고, 스레드별로 성공했으면 null, 실패했으면 예외를 돌려준다.
     */
    private List<Throwable> createConcurrently(List<LocalDateTime> startAts) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(startAts.size());
        CountDownLatch ready = new CountDownLatch(startAts.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (LocalDateTime startAt : startAts) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        helpRequestService.create(accountId,
                                new HelpRequestCreateRequestDto(startAt, Set.of(HelpType.SERVING), null, null));
                        return null;
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            ready.await();
            start.countDown();

            List<Throwable> results = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdown();
        }
    }

    private void assertOneCreatedOthersOverlap(List<Throwable> results) {
        assertThat(results).filteredOn(result -> result == null).hasSize(1);
        assertThat(results).filteredOn(result -> result != null)
                .allSatisfy(error -> assertThat(error)
                        .isInstanceOf(HelpRequestException.class)
                        .extracting("errorCode").isEqualTo(HelpRequestErrorType.TIME_OVERLAP));
        assertThat(helpRequestRepository.count()).isEqualTo(1);
    }

    @Test
    void create_겹치는신청동시에두건_한건만생성() throws Exception {
        // when
        List<Throwable> results = createConcurrently(List.of(bookableNoon, bookableNoon.plusMinutes(30)));

        // then
        assertOneCreatedOthersOverlap(results);
    }

    @Test
    void create_같은신청을동시에세번_한건만생성() throws Exception {
        // when — 버튼을 여러 번 누른 경우
        List<Throwable> results = createConcurrently(List.of(bookableNoon, bookableNoon, bookableNoon));

        // then
        assertOneCreatedOthersOverlap(results);
    }
}
