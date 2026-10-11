package com.hankkiatti.domain.helper.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.admin.entity.AdminGrade;
import com.hankkiatti.domain.admin.repository.AdminRepository;
import com.hankkiatti.domain.application.dto.response.ApplyResponseDto;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationAfterAction;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.entity.CancelReason;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.application.service.ApplicationService;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.auth.repository.PasswordResetTokenRepository;
import com.hankkiatti.domain.auth.repository.RefreshTokenRepository;
import com.hankkiatti.domain.helper.dto.response.AdminHelperDeactivationResponseDto;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helper.service.AdminHelperDeactivationService;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.mail.repository.MailOutboxRepository;
import com.hankkiatti.domain.notification.entity.NotificationJob;
import com.hankkiatti.domain.notification.entity.NotificationJobType;
import com.hankkiatti.domain.notification.repository.NotificationJobRepository;
import com.hankkiatti.domain.notification.repository.NotificationRepository;
import com.hankkiatti.domain.sms.repository.SmsOutboxRepository;
import com.hankkiatti.domain.student.entity.DisabilityType;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.support.TestProfiles;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 도우미 비활성화는 계정과 지원마다 트랜잭션을 나눠 커밋하므로, 테스트도 커밋하고 끝나면 지운다.
 * 실제 지원 흐름으로 만든 매칭·예비를 비활성화해 승격·모집 재개·빠짐과 알림 작업, 로그인 차단, 동시 지원을 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminHelperDeactivationIntegrationTest {

    private static final String PASSWORD = "hankki!2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private AdminHelperDeactivationService adminHelperDeactivationService;

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
    private NotificationJobRepository notificationJobRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MailOutboxRepository mailOutboxRepository;

    @Autowired
    private SmsOutboxRepository smsOutboxRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    private final List<Long> accountIds = new ArrayList<>();
    private final List<Long> helperIds = new ArrayList<>();
    private Long adminId;
    private Student student;
    private LocalDateTime tomorrowNoon;

    @BeforeEach
    void setUp() {
        Account adminAccount = saveAccount("deact-admin", AccountRole.ADMIN);
        adminId = adminRepository.save(new Admin(adminAccount, "이운영", AdminGrade.LIMITED)).getAccountId();
        student = studentRepository.save(new Student(saveAccount("60239995", AccountRole.STUDENT), "이서준",
                "60239995", "010-1234-5678", "seojun", "60239995@mju.ac.kr", DisabilityType.VISUAL, null));
        for (int i = 0; i < 3; i++) {
            Helper helper = helperRepository.save(TestProfiles.helper(
                    saveAccount("deact-helper" + i + "@mju.ac.kr", AccountRole.HELPER), "6029990" + i));
            helperIds.add(helper.getAccountId());
        }
        tomorrowNoon = LocalDate.now(clock).plusDays(1).atTime(12, 0);
    }

    @AfterEach
    void tearDown() {
        notificationJobRepository.deleteAll();
        notificationRepository.deleteAll();
        mailOutboxRepository.deleteAll();
        smsOutboxRepository.deleteAll();
        applicationRepository.deleteAll();
        helpRequestRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        helperRepository.deleteAllById(helperIds);
        studentRepository.deleteById(student.getAccountId());
        adminRepository.deleteById(adminId);
        accountRepository.deleteAllById(accountIds);
    }

    private Account saveAccount(String loginId, AccountRole role) {
        Account account = accountRepository.save(
                new Account(loginId, passwordEncoder.encode(PASSWORD), role, false, false));
        accountIds.add(account.getId());
        return account;
    }

    private String login(String path, String loginId) throws Exception {
        String body = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private HelpRequest saveRequest(LocalDateTime startAt) {
        return helpRequestRepository.save(new HelpRequest(student, startAt, Set.of(HelpType.SERVING), null, null));
    }

    private Long apply(int helperIndex, HelpRequest request) {
        return applicationService.apply(helperIds.get(helperIndex), request.getId()).applicationId();
    }

    private List<NotificationJob> jobs(NotificationJobType type, Long targetId) {
        return notificationJobRepository.findAll().stream()
                .filter(job -> job.getType() == type && targetId.equals(job.getTargetId()))
                .toList();
    }

    @Test
    void 비활성화_미리보기대로매칭은승격과모집재개_예비는빠짐_알림작업_로그인차단_재활성화() throws Exception {
        // given — 도우미0: 내일 매칭(예비 도우미1 있음), 모레 매칭(예비 없음), 글피 도우미2 뒤 예비 1번
        HelpRequest promotedRequest = saveRequest(tomorrowNoon);
        Long matchedWithWaiting = apply(0, promotedRequest);
        Long promotedCandidate = apply(1, promotedRequest);
        HelpRequest reopenedRequest = saveRequest(tomorrowNoon.plusDays(1));
        Long matchedAlone = apply(0, reopenedRequest);
        HelpRequest waitedRequest = saveRequest(tomorrowNoon.plusDays(2));
        apply(2, waitedRequest);
        Long waiting = apply(0, waitedRequest);
        String helperToken = login("/api/auth/login", "deact-helper0@mju.ac.kr");
        String adminToken = login("/api/admin/auth/login", "deact-admin");
        String path = "/api/admin/helpers/" + helperIds.get(0);

        // when & then — 미리보기 (제한 권한)
        mockMvc.perform(get(path + "/deactivation-preview").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyInactive").value(false))
                .andExpect(jsonPath("$.data.matchedCount").value(2))
                .andExpect(jsonPath("$.data.waitingCount").value(1))
                .andExpect(jsonPath("$.data.targets[0].studentName").value("이서준"))
                .andExpect(jsonPath("$.data.targets[2].status").value("WAITING"))
                .andExpect(jsonPath("$.data.targets[2].waitingOrder").value(1));

        // 비활성화
        mockMvc.perform(post(path + "/deactivate").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.releasedMatchCount").value(2))
                .andExpect(jsonPath("$.data.releasedWaitingCount").value(1))
                .andExpect(jsonPath("$.data.remainingCount").value(0));

        Application canceled = applicationRepository.findById(matchedWithWaiting).orElseThrow();
        assertThat(canceled.getStatus()).isEqualTo(ApplicationStatus.HELPER_CANCELED);
        assertThat(canceled.getCancelReason()).isEqualTo(CancelReason.ADMIN_DEACTIVATED);
        assertThat(canceled.getAfterAction()).isEqualTo(ApplicationAfterAction.PROMOTED);
        assertThat(applicationRepository.findById(promotedCandidate).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.MATCHED);
        assertThat(applicationRepository.findById(matchedAlone).orElseThrow().getAfterAction())
                .isEqualTo(ApplicationAfterAction.REOPENED);
        assertThat(helpRequestRepository.findById(reopenedRequest.getId()).orElseThrow().getStatus())
                .isEqualTo(HelpRequestStatus.RECRUITING);
        assertThat(applicationRepository.findById(waiting).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.WITHDRAWN);
        // 도우미 취소와 같은 알림: 승격된 도우미·장애학생 "도우미 바뀜", 장애학생 "다시 모집 중"
        assertThat(jobs(NotificationJobType.HELPER_CONFIRMED, promotedCandidate)).hasSize(1);
        assertThat(jobs(NotificationJobType.REQUEST_REOPENED, reopenedRequest.getId())).hasSize(1);

        // 로그인·기존 토큰 차단, 요약 취소 건수에 넣지 않음, 다시 보내면 409
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"deact-helper0@mju.ac.kr\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_DEACTIVATED"));
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, helperToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.data.summary.canceledCount").value(0));
        mockMvc.perform(post(path + "/deactivate").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HELPER_ALREADY_INACTIVE"));

        // 재활성화하면 다시 로그인된다
        mockMvc.perform(post(path + "/activate").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"deact-helper0@mju.ac.kr\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(path + "/activate").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HELPER_ALREADY_ACTIVE"));
    }

    @Test
    void 비활성화와동시지원_교착없이끝나고진행중지원이남지않는다() throws Exception {
        // given — 도우미0 매칭 1건(예비 도우미1), 새로 지원할 모집 중 신청 2건
        HelpRequest matched = saveRequest(tomorrowNoon);
        apply(0, matched);
        apply(1, matched);
        HelpRequest open1 = saveRequest(tomorrowNoon.plusDays(1));
        HelpRequest open2 = saveRequest(tomorrowNoon.plusDays(2));
        List<Callable<Object>> tasks = List.of(
                () -> adminHelperDeactivationService.deactivate(adminId, helperIds.get(0)),
                () -> applicationService.apply(helperIds.get(0), open1.getId()),
                () -> applicationService.apply(helperIds.get(0), open2.getId()),
                () -> applicationService.apply(helperIds.get(2), matched.getId()));

        // when
        List<Object> results = runConcurrently(tasks);

        // then — 비활성화는 성공, 도우미0의 지원은 성공했으면 해제됐고 아니면 비활성으로 막혔다
        assertThat(results.get(0)).isInstanceOf(AdminHelperDeactivationResponseDto.class);
        for (Object result : results.subList(1, 3)) {
            if (result instanceof AuthException exception) {
                assertThat(exception.getErrorCode()).isEqualTo(AuthErrorType.ACCOUNT_DEACTIVATED);
            } else {
                assertThat(result).isInstanceOf(ApplyResponseDto.class);
            }
        }
        assertThat(results.get(3)).isInstanceOf(ApplyResponseDto.class);
        assertThat(applicationRepository.findActiveIdsBeforeMeal(helperIds.get(0), LocalDateTime.now(clock)))
                .isEmpty();
        assertThat(applicationRepository.findByHelpRequestIdAndStatus(matched.getId(), ApplicationStatus.MATCHED))
                .singleElement()
                .satisfies(application -> assertThat(application.getHelper().getAccountId()).isEqualTo(helperIds.get(1)));
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
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdown();
        }
    }
}
