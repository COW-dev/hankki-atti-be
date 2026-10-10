package com.hankkiatti.domain.helprequest.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.application.service.ApplicationService;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.support.TestProfiles;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 SecurityFilterChain과 DB로 장애학생 매칭 취소를 확인한다: 신청은 취소, 매칭·예비 도우미는 학생 사정 취소.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HelpRequestCancelIntegrationTest {

    private static final String PASSWORD = "hankki!2026";
    private static final String STUDENT_NO = "60231234";

    @Autowired
    private MockMvc mockMvc;

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    private Student student;
    private LocalDateTime tomorrowNoon;
    // 0: 매칭, 1: 예비
    private final List<Helper> helpers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        student = studentRepository.save(TestProfiles.student(saveAccount(STUDENT_NO, AccountRole.STUDENT)));
        tomorrowNoon = LocalDateTime.now(clock).plusDays(1).truncatedTo(ChronoUnit.DAYS).withHour(12);
        for (int i = 0; i < 2; i++) {
            helpers.add(helperRepository.save(
                    TestProfiles.helper(saveAccount(email(i), AccountRole.HELPER), "6023000" + i)));
        }
    }

    private static String email(int index) {
        return "student-cancel" + index + "@mju.ac.kr";
    }

    private Account saveAccount(String loginId, AccountRole role) {
        return accountRepository.save(new Account(loginId, passwordEncoder.encode(PASSWORD), role, false, false));
    }

    private String loginBearer(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private HelpRequest saveRequest() {
        return helpRequestRepository.save(new HelpRequest(student, tomorrowNoon, Set.of(HelpType.SERVING), null, null));
    }

    private String cancelPath(HelpRequest request) {
        return "/api/help-requests/" + request.getId() + "/cancel";
    }

    @Test
    void 매칭취소_신청은취소되고_매칭과예비도우미는학생사정취소로지난활동에() throws Exception {
        // given
        HelpRequest request = saveRequest();
        helpers.forEach(helper -> applicationService.apply(helper.getAccountId(), request.getId()));
        String token = loginBearer(STUDENT_NO);

        // when
        mockMvc.perform(post(cancelPath(request)).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(request.getId()))
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.helper").doesNotExist());

        // then — 장애학생 내 신청은 지난 목록, 두 도우미 모두 지난 활동에 학생 사정 취소 (장애학생 정보 없음)
        mockMvc.perform(get("/api/help-requests/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.data.upcoming.length()").value(0))
                .andExpect(jsonPath("$.data.past[0].status").value("CANCELED"));
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/applications/me").header(HttpHeaders.AUTHORIZATION, loginBearer(email(i))))
                    .andExpect(jsonPath("$.data.inProgress.length()").value(0))
                    .andExpect(jsonPath("$.data.past[0].status").value("STUDENT_CANCELED"))
                    .andExpect(jsonPath("$.data.past[0].student").doesNotExist());
        }
    }

    @Test
    void 매칭취소_모집중이면409_도우미계정이면403() throws Exception {
        // given
        HelpRequest recruiting = saveRequest();

        // when & then
        mockMvc.perform(post(cancelPath(recruiting)).header(HttpHeaders.AUTHORIZATION, loginBearer(STUDENT_NO)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HELP_REQUEST_INVALID_STATUS"));
        mockMvc.perform(post(cancelPath(recruiting)).header(HttpHeaders.AUTHORIZATION, loginBearer(email(0))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
    }
}
