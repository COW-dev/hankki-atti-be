package com.hankkiatti.domain.application.integration;

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
 * 실제 SecurityFilterChain과 DB로 예비 빠지기를 확인한다: 뒤 순번 당김, 지난 활동 표시, 다시 지원.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LeaveWaitingIntegrationTest {

    private static final String PASSWORD = "hankki!2026";

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

    private HelpRequest request;
    // 0: 매칭, 1·2: 예비 1·2번
    private final List<Long> applicationIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Student student = studentRepository.save(TestProfiles.student(saveAccount("60231234", AccountRole.STUDENT)));
        LocalDateTime tomorrowNoon = LocalDateTime.now(clock).plusDays(1).truncatedTo(ChronoUnit.DAYS).withHour(12);
        request = helpRequestRepository.save(new HelpRequest(student, tomorrowNoon, Set.of(HelpType.SERVING), null, null));
        for (int i = 0; i < 3; i++) {
            Helper helper = helperRepository.save(
                    TestProfiles.helper(saveAccount(email(i), AccountRole.HELPER), "6023000" + i));
            applicationIds.add(applicationService.apply(helper.getAccountId(), request.getId()).applicationId());
        }
    }

    private static String email(int index) {
        return "leave" + index + "@mju.ac.kr";
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

    private String leavePath(int index) {
        return "/api/applications/" + applicationIds.get(index) + "/leave";
    }

    @Test
    void 예비빠지기_뒤순번당겨지고지난활동에빠짐_다시지원하면맨뒤() throws Exception {
        // given
        String first = loginBearer(email(1));
        String second = loginBearer(email(2));

        // when — 예비 1번이 빠진다
        mockMvc.perform(post(leavePath(1)).header(HttpHeaders.AUTHORIZATION, first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationId").value(applicationIds.get(1)))
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.data.student").doesNotExist());

        // then — 예비 2번이 1번으로 당겨지고, 빠진 건 지난 활동에 남는다
        mockMvc.perform(get("/api/applications/me").header(HttpHeaders.AUTHORIZATION, second))
                .andExpect(jsonPath("$.data.inProgress[0].waitingOrder").value(1));
        mockMvc.perform(get("/api/applications/me").param("filter", "PAST").header(HttpHeaders.AUTHORIZATION, first))
                .andExpect(jsonPath("$.data.past[0].status").value("WITHDRAWN"));

        // when & then — 다시 지원하면 맨 뒤 예비
        mockMvc.perform(post("/api/help-requests/" + request.getId() + "/applications")
                        .header(HttpHeaders.AUTHORIZATION, first))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.waitingOrder").value(2));
    }

    @Test
    void 예비빠지기_매칭완료면409_남의지원이면404() throws Exception {
        // when & then
        mockMvc.perform(post(leavePath(0)).header(HttpHeaders.AUTHORIZATION, loginBearer(email(0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_INVALID_STATUS"));
        mockMvc.perform(post(leavePath(1)).header(HttpHeaders.AUTHORIZATION, loginBearer(email(2))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
    }
}
