package com.hankkiatti.domain.helprequest.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 SecurityFilterChain으로 노쇼 신고를 확인한다. 이용 완료 신청과 지원을 직접 넣는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HelpRequestNoShowIntegrationTest {

    private static final String PASSWORD = "hankki!2026";
    private static final String STUDENT_NO = "60231234";
    private static final String HELPER_EMAIL = "helper@mju.ac.kr";

    @Autowired
    private MockMvc mockMvc;

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    private Student student;
    private Helper helper;

    @BeforeEach
    void setUp() {
        student = studentRepository.save(TestProfiles.student(saveAccount(STUDENT_NO, AccountRole.STUDENT)));
        helper = helperRepository.save(TestProfiles.helper(saveAccount(HELPER_EMAIL, AccountRole.HELPER), "60230001"));
    }

    private Account saveAccount(String loginId, AccountRole role) {
        return accountRepository.save(new Account(loginId, passwordEncoder.encode(PASSWORD), role, false, false));
    }

    // completedAt에 이용 완료된 신청과 도우미 지원(봉사시간 1.0)
    private HelpRequest saveCompletedRequest(LocalDateTime completedAt) {
        LocalDateTime startAt = completedAt.minusHours(1);
        HelpRequest request = new HelpRequest(student, startAt, Set.of(HelpType.SERVING), null, null);
        request.match(startAt.minusDays(1));
        request.complete(completedAt);
        helpRequestRepository.save(request);
        Application application = new Application(request, helper, startAt.minusDays(1));
        application.match(startAt.minusDays(1));
        application.complete();
        applicationRepository.save(application);
        return request;
    }

    private String loginBearer(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private ResultActions reportNoShow(String token, Long helpRequestId) throws Exception {
        return mockMvc.perform(post("/api/help-requests/" + helpRequestId + "/no-show")
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    @Test
    void 노쇼신고_노쇼로바뀐신청을돌려주고도우미봉사시간이0() throws Exception {
        // given
        HelpRequest request = saveCompletedRequest(LocalDateTime.now(clock).minusHours(2));
        String studentToken = loginBearer(STUDENT_NO);
        String helperToken = loginBearer(HELPER_EMAIL);
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, helperToken))
                .andExpect(jsonPath("$.data.helper.volunteerHours").value(1.0));

        // when
        reportNoShow(studentToken, request.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NO_SHOW"))
                .andExpect(jsonPath("$.data.helper.name").value("이도움"))
                .andExpect(jsonPath("$.data.noShowReportable").value(false));

        // then
        mockMvc.perform(get("/api/help-requests/me").header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(jsonPath("$.data.past[0].status").value("NO_SHOW"));
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, helperToken))
                .andExpect(jsonPath("$.data.helper.volunteerHours").value(0.0));
        reportNoShow(studentToken, request.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HELP_REQUEST_INVALID_STATUS"));
    }

    @Test
    void 이용완료후24시간지남_409() throws Exception {
        // given
        HelpRequest request = saveCompletedRequest(LocalDateTime.now(clock).minusHours(25));
        String token = loginBearer(STUDENT_NO);

        // when & then
        reportNoShow(token, request.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HELP_REQUEST_NO_SHOW_PERIOD_EXPIRED"));
    }

    @Test
    void 도우미가신고_403() throws Exception {
        // given
        HelpRequest request = saveCompletedRequest(LocalDateTime.now(clock).minusHours(2));
        String token = loginBearer(HELPER_EMAIL);

        // when & then
        reportNoShow(token, request.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
    }
}
