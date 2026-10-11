package com.hankkiatti.domain.application.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.admin.entity.AdminGrade;
import com.hankkiatti.domain.admin.repository.AdminRepository;
import com.hankkiatti.domain.application.dto.request.VolunteerHoursUpdateRequestDto;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.entity.VolunteerHourChange;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.application.repository.VolunteerHourChangeRepository;
import com.hankkiatti.domain.application.service.AdminVolunteerHourService;
import com.hankkiatti.domain.auth.repository.RefreshTokenRepository;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.helprequest.service.HelpRequestService;
import com.hankkiatti.domain.mail.repository.MailOutboxRepository;
import com.hankkiatti.domain.notification.repository.NotificationJobRepository;
import com.hankkiatti.domain.notification.repository.NotificationRepository;
import com.hankkiatti.domain.sms.repository.SmsOutboxRepository;
import com.hankkiatti.domain.student.entity.DisabilityType;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.support.TestProfiles;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Clock;
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
 * 봉사시간 수정을 실제 SecurityFilterChain·DB로 확인한다. 노쇼 신고와 동시에 들어오는 경우를 보려고 커밋하고 끝나면 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminVolunteerHourIntegrationTest {

    private static final String PASSWORD = "hankki!2026";
    private static final int RACE_ROUNDS = 10;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminVolunteerHourService adminVolunteerHourService;

    @Autowired
    private HelpRequestService helpRequestService;

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
    private VolunteerHourChangeRepository volunteerHourChangeRepository;

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    private final List<Long> accountIds = new ArrayList<>();
    private Long fullAdminId;
    private Long limitedAdminId;
    private Student student;
    private Helper helper;

    @BeforeEach
    void setUp() {
        fullAdminId = adminRepository.save(new Admin(saveAccount("hours-full", AccountRole.ADMIN), "김센터",
                AdminGrade.FULL)).getAccountId();
        limitedAdminId = adminRepository.save(new Admin(saveAccount("hours-limited", AccountRole.ADMIN), "이운영",
                AdminGrade.LIMITED)).getAccountId();
        student = studentRepository.save(new Student(saveAccount("60239994", AccountRole.STUDENT), "김민지",
                "60239994", "010-1234-1234", "minji", "60239994@mju.ac.kr", DisabilityType.VISUAL, null));
        helper = helperRepository.save(TestProfiles.helper(saveAccount("hours-helper@mju.ac.kr", AccountRole.HELPER),
                "60299994"));
    }

    @AfterEach
    void tearDown() {
        volunteerHourChangeRepository.deleteAll();
        notificationJobRepository.deleteAll();
        notificationRepository.deleteAll();
        mailOutboxRepository.deleteAll();
        smsOutboxRepository.deleteAll();
        applicationRepository.deleteAll();
        helpRequestRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        helperRepository.deleteById(helper.getAccountId());
        studentRepository.deleteById(student.getAccountId());
        adminRepository.deleteAllById(List.of(fullAdminId, limitedAdminId));
        accountRepository.deleteAllById(accountIds);
    }

    private Account saveAccount(String loginId, AccountRole role) {
        Account account = accountRepository.save(
                new Account(loginId, passwordEncoder.encode(PASSWORD), role, false, false));
        accountIds.add(account.getId());
        return account;
    }

    private String adminToken(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    // 오늘 아침 식사가 끝나 이용 완료된 건 (노쇼 신고 가능 기간 안)
    private Application completedApplication() {
        LocalDateTime start = LocalDateTime.now(clock).minusHours(3);
        HelpRequest request = new HelpRequest(student, start, Set.of(HelpType.SERVING), null, null);
        request.match(start.minusDays(1));
        request.complete(start.plusHours(1));
        helpRequestRepository.save(request);
        Application application = new Application(request, helper, start.minusDays(1));
        application.match(start.minusDays(1));
        application.complete();
        return applicationRepository.save(application);
    }

    private String body(String hours, String reason) {
        return "{\"hours\":" + hours + ",\"reason\":" + (reason == null ? "null" : "\"" + reason + "\"") + "}";
    }

    @Test
    void 수정_전체권한은합계와활동이력에반영_제한권한403_검증오류422() throws Exception {
        // given
        Application application = completedApplication();
        String path = "/api/admin/applications/" + application.getId() + "/volunteer-hours";
        String full = adminToken("hours-full");

        // when & then
        mockMvc.perform(patch(path).header(HttpHeaders.AUTHORIZATION, full)
                        .contentType(MediaType.APPLICATION_JSON).content(body("1.5", "식사 보조가 길어졌어요")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.volunteerHours").value(1.5))
                .andExpect(jsonPath("$.data.change.beforeHours").value(1.0))
                .andExpect(jsonPath("$.data.change.adminName").value("김센터"));

        mockMvc.perform(get("/api/admin/helpers/" + helper.getAccountId()).header(HttpHeaders.AUTHORIZATION, full))
                .andExpect(jsonPath("$.data.summary.volunteerHours").value(1.5));
        String limited = adminToken("hours-limited");
        mockMvc.perform(get("/api/admin/helpers/" + helper.getAccountId() + "/activities")
                        .header(HttpHeaders.AUTHORIZATION, limited))
                .andExpect(jsonPath("$.data[0].volunteerHours").value(1.5))
                .andExpect(jsonPath("$.data[0].volunteerHourChanges[0].afterHours").value(1.5))
                .andExpect(jsonPath("$.data[0].volunteerHourChanges[0].reason").value("식사 보조가 길어졌어요"));

        mockMvc.perform(patch(path).header(HttpHeaders.AUTHORIZATION, limited)
                        .contentType(MediaType.APPLICATION_JSON).content(body("2.0", "사유")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
        mockMvc.perform(patch(path).header(HttpHeaders.AUTHORIZATION, full)
                        .contentType(MediaType.APPLICATION_JSON).content(body("1.3", "사유")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("APPLICATION_INVALID_VOLUNTEER_HOURS"));
        mockMvc.perform(patch(path).header(HttpHeaders.AUTHORIZATION, full)
                        .contentType(MediaType.APPLICATION_JSON).content(body("11", "사유")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
        mockMvc.perform(patch(path).header(HttpHeaders.AUTHORIZATION, full)
                        .contentType(MediaType.APPLICATION_JSON).content(body("2.0", " ")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
        mockMvc.perform(patch(path).header(HttpHeaders.AUTHORIZATION, full)
                        .contentType(MediaType.APPLICATION_JSON).content(body("1.5", "사유")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("APPLICATION_VOLUNTEER_HOURS_UNCHANGED"));
        assertThat(volunteerHourChangeRepository.findAll()).hasSize(1);
    }

    @Test
    void 수정과노쇼신고동시_한줄로서서상태와시간과이력이맞는다() throws Exception {
        // 실행 순서가 매번 달라지도록 여러 번 겨룬다
        for (int round = 0; round < RACE_ROUNDS; round++) {
            // given
            Application application = completedApplication();
            Long helpRequestId = application.getHelpRequest().getId();
            List<Callable<Object>> tasks = List.of(
                    () -> adminVolunteerHourService.change(fullAdminId, application.getId(),
                            new VolunteerHoursUpdateRequestDto(new BigDecimal("2.0"), "실제로 두 시간 도왔어요")),
                    () -> helpRequestService.reportNoShow(student.getAccountId(), helpRequestId));

            // when
            List<Object> results = runConcurrently(tasks);

            // then — 둘 다 성공. 수정이 먼저면 노쇼가 0으로, 노쇼가 먼저면 수정이 0 → 2.0으로
            assertThat(results).noneMatch(Exception.class::isInstance);
            Application after = applicationRepository.findById(application.getId()).orElseThrow();
            assertThat(after.getStatus()).isEqualTo(ApplicationStatus.NO_SHOW);
            VolunteerHourChange change = volunteerHourChangeRepository.findAll().stream()
                    .filter(found -> found.getApplication().getId().equals(application.getId()))
                    .findFirst().orElseThrow();
            if (change.getBeforeHours().compareTo(BigDecimal.ONE) == 0) {
                assertThat(after.getVolunteerHours()).isEqualByComparingTo("0.0");
            } else {
                assertThat(change.getBeforeHours()).isEqualByComparingTo("0.0");
                assertThat(after.getVolunteerHours()).isEqualByComparingTo("2.0");
            }
        }
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
