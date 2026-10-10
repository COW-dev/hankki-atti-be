package com.hankkiatti.domain.student.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.admin.entity.AdminGrade;
import com.hankkiatti.domain.admin.repository.AdminRepository;
import com.hankkiatti.domain.application.dto.response.ApplyResponseDto;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.exception.ApplicationErrorType;
import com.hankkiatti.domain.application.exception.ApplicationException;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.application.service.ApplicationService;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.student.dto.response.AdminStudentAccountStatusResponseDto;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.domain.student.service.AdminStudentService;
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

@SpringBootTest
class AdminStudentDeactivationConcurrencyTest {

    @Autowired
    private AdminStudentService adminStudentService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AdminRepository adminRepository;

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

    private Long adminId;
    private Long studentId;
    private Long helperId;
    private Long helpRequestId;

    @BeforeEach
    void setUp() {
        Account adminAccount = accountRepository.save(
                new Account("admin-deactivation", "hash", AccountRole.ADMIN, false, false));
        adminId = adminRepository.save(new Admin(adminAccount, "관리자", AdminGrade.FULL)).getAccountId();

        Account studentAccount = accountRepository.save(
                new Account("60239996", "hash", AccountRole.STUDENT, false, false));
        Student student = studentRepository.save(TestProfiles.student(studentAccount));
        studentId = student.getAccountId();

        Account helperAccount = accountRepository.save(
                new Account("deactivation-helper@mju.ac.kr", "hash", AccountRole.HELPER, false, false));
        Helper helper = helperRepository.save(TestProfiles.helper(helperAccount, "60299996"));
        helperId = helper.getAccountId();

        LocalDateTime tomorrowNoon = LocalDateTime.now(clock)
                .plusDays(1)
                .truncatedTo(ChronoUnit.DAYS)
                .withHour(12);
        helpRequestId = helpRequestRepository.save(
                new HelpRequest(student, tomorrowNoon, Set.of(HelpType.SERVING), null, null)).getId();
    }

    @AfterEach
    void tearDown() {
        applicationRepository.deleteAll();
        helpRequestRepository.deleteAll();
        helperRepository.deleteById(helperId);
        studentRepository.deleteById(studentId);
        adminRepository.deleteById(adminId);
        accountRepository.deleteAllById(List.of(helperId, studentId, adminId));
    }

    @Test
    void deactivate_동시지원_비활성계정과취소된신청만남는다() throws Exception {
        // given
        List<Callable<Object>> tasks = List.of(
                () -> adminStudentService.deactivate(adminId, studentId),
                () -> applicationService.apply(helperId, helpRequestId));

        // when
        List<Object> results = runConcurrently(tasks);

        // then
        assertThat(results).filteredOn(AdminStudentAccountStatusResponseDto.class::isInstance).hasSize(1);
        assertThat(results).allMatch(result -> result instanceof AdminStudentAccountStatusResponseDto
                || result instanceof ApplyResponseDto
                || result instanceof ApplicationException);
        results.stream().filter(ApplicationException.class::isInstance)
                .map(ApplicationException.class::cast)
                .forEach(exception -> assertThat(exception.getErrorCode()).isEqualTo(ApplicationErrorType.NOT_OPEN));
        assertThat(accountRepository.findById(studentId).orElseThrow().getStatus()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(helpRequestRepository.findById(helpRequestId).orElseThrow().getStatus())
                .isEqualTo(HelpRequestStatus.CANCELED);
        assertThat(applicationRepository.findAll())
                .allSatisfy(application -> assertThat(application.getStatus())
                        .isEqualTo(ApplicationStatus.STUDENT_CANCELED));
    }

    private List<Object> runConcurrently(List<Callable<Object>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return task.call();
                    } catch (Exception exception) {
                        return exception;
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
}
