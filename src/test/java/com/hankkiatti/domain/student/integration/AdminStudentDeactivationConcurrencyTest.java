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
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.exception.ApplicationErrorType;
import com.hankkiatti.domain.application.exception.ApplicationException;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.application.service.ApplicationService;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.dto.request.HelpRequestCreateRequestDto;
import com.hankkiatti.domain.helprequest.dto.response.HelpRequestCreateResponseDto;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.helprequest.service.HelpRequestSchedule;
import com.hankkiatti.domain.helprequest.service.HelpRequestService;
import com.hankkiatti.domain.mail.repository.MailOutboxRepository;
import com.hankkiatti.domain.notification.entity.NotificationJob;
import com.hankkiatti.domain.notification.entity.NotificationJobType;
import com.hankkiatti.domain.notification.repository.NotificationJobRepository;
import com.hankkiatti.domain.notification.repository.NotificationRepository;
import com.hankkiatti.domain.sms.repository.SmsOutboxRepository;
import com.hankkiatti.domain.student.dto.response.AdminStudentAccountStatusResponseDto;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.domain.student.service.AdminStudentService;
import com.hankkiatti.support.TestProfiles;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private HelpRequestService helpRequestService;

    @Autowired
    private HelpRequestSchedule helpRequestSchedule;

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
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationJobRepository notificationJobRepository;

    @Autowired
    private MailOutboxRepository mailOutboxRepository;

    @Autowired
    private SmsOutboxRepository smsOutboxRepository;

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
        notificationJobRepository.deleteAll();
        notificationRepository.deleteAll();
        mailOutboxRepository.deleteAll();
        smsOutboxRepository.deleteAll();
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

    @Test
    void deactivate_동시신청생성_비활성계정의진행중신청이남지않는다() throws Exception {
        // given — setUp의 내일 점심 신청과 겹치지 않는 저녁 시각
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime bookableDinner = helpRequestSchedule.bookableStartTimes(now).stream()
                .filter(startAt -> startAt.toLocalDate().isAfter(now.toLocalDate())
                        && startAt.toLocalTime().equals(LocalTime.of(17, 0)))
                .findFirst()
                .orElseThrow();
        List<Callable<Object>> tasks = List.of(
                () -> adminStudentService.deactivate(adminId, studentId),
                () -> helpRequestService.create(studentId,
                        new HelpRequestCreateRequestDto(bookableDinner, Set.of(HelpType.SERVING), null, null)));

        // when
        List<Object> results = runConcurrently(tasks);

        // then — 신청이 먼저 잠그면 비활성화가 그 신청까지 취소하고, 비활성화가 먼저면 신청이 막힌다
        assertThat(results).filteredOn(AdminStudentAccountStatusResponseDto.class::isInstance).hasSize(1);
        assertThat(results).allMatch(result -> result instanceof AdminStudentAccountStatusResponseDto
                || result instanceof HelpRequestCreateResponseDto
                || result instanceof AuthException);
        results.stream().filter(AuthException.class::isInstance)
                .map(AuthException.class::cast)
                .forEach(exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorType.ACCOUNT_DEACTIVATED));
        assertThat(helpRequestRepository.findAll())
                .noneMatch(request -> request.getStatus().isInProgress());
    }

    @Test
    void deactivate_매칭된도우미에게학생취소알림작업을남긴다() {
        // given
        Long applicationId = applicationService.apply(helperId, helpRequestId).applicationId();

        // when
        adminStudentService.deactivate(adminId, studentId);

        // then — 장애학생 매칭 취소와 같은 "학생 취소" 알림 (#37)
        assertThat(notificationJobRepository.findAll())
                .filteredOn(job -> job.getType() == NotificationJobType.STUDENT_CANCELED)
                .extracting(NotificationJob::getTargetId)
                .containsExactly(applicationId);
    }

    @Test
    void deactivate_식사가시작된매칭건_취소하지않고자동처리에맡긴다() {
        // given — 20분 전에 시작한 식사에 도우미가 매칭돼 있다
        LocalDateTime now = LocalDateTime.now(clock);
        Student student = studentRepository.findById(studentId).orElseThrow();
        HelpRequest started = new HelpRequest(student, now.minusMinutes(20), Set.of(HelpType.SERVING), null, null);
        started.match(now.minusDays(1));
        Long startedId = helpRequestRepository.save(started).getId();
        Application matched = new Application(started, helperRepository.findById(helperId).orElseThrow(),
                now.minusDays(1));
        matched.match(now.minusDays(1));
        Long matchedId = applicationRepository.save(matched).getId();

        // when
        adminStudentService.deactivate(adminId, studentId);

        // then — 시작 전 신청만 취소되고, 식사 중인 매칭은 그대로라 식사 종료 때 봉사시간이 기록된다
        assertThat(helpRequestRepository.findById(helpRequestId).orElseThrow().getStatus())
                .isEqualTo(HelpRequestStatus.CANCELED);
        assertThat(helpRequestRepository.findById(startedId).orElseThrow().getStatus())
                .isEqualTo(HelpRequestStatus.MATCHED);
        assertThat(applicationRepository.findById(matchedId).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.MATCHED);
        assertThat(notificationJobRepository.findAll())
                .noneMatch(job -> job.getType() == NotificationJobType.STUDENT_CANCELED);
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
