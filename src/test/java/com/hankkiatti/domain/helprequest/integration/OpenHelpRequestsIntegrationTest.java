package com.hankkiatti.domain.helprequest.integration;

import static org.hamcrest.Matchers.nullValue;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Set;
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
 * 실제 SecurityFilterChain으로 도우미 요청 목록을 확인한다. 블라인드 필드가 응답에 없는지까지 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OpenHelpRequestsIntegrationTest {

    private static final String PASSWORD = "hankki!2026";
    private static final String OPEN_REQUESTS = "/api/help-requests/open";
    private static final String HELPER_LOGIN = "helper@mju.ac.kr";
    private static final DateTimeFormatter JSON_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

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

    private Account saveAccount(String loginId, AccountRole role) {
        return accountRepository.save(new Account(loginId, passwordEncoder.encode(PASSWORD), role, false, false));
    }

    private Student saveStudent(String studentNo) {
        return studentRepository.save(TestProfiles.student(saveAccount(studentNo, AccountRole.STUDENT)));
    }

    private Helper saveHelper() {
        return helperRepository.save(TestProfiles.helper(saveAccount(HELPER_LOGIN, AccountRole.HELPER), "60230001"));
    }

    private String loginBearer(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private HelpRequest saveRequest(Student student, LocalDateTime startAt) {
        return helpRequestRepository.save(
                new HelpRequest(student, startAt, Set.of(HelpType.OTHER), "식판 반납", "출입구에서 기다릴게요"));
    }

    @Test
    void 요청목록_날짜별카드에블라인드필드만_지원결과포함() throws Exception {
        // given
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.HOURS);
        Student student = saveStudent("60231234");
        Helper me = saveHelper();
        LocalDateTime tomorrowNoon = now.toLocalDate().plusDays(1).atTime(12, 0);

        HelpRequest recruiting = saveRequest(student, tomorrowNoon);
        HelpRequest myMatched = saveRequest(student, tomorrowNoon.plusHours(5));
        myMatched.match(now);
        Application matched = new Application(myMatched, me, now);
        matched.match(now);
        applicationRepository.save(matched);
        HelpRequest overlapping = saveRequest(student, tomorrowNoon.plusHours(5).plusMinutes(30));
        saveRequest(student, now.minusHours(1));
        String token = loginBearer(HELPER_LOGIN);

        // when & then
        mockMvc.perform(get(OPEN_REQUESTS).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].date").value(tomorrowNoon.toLocalDate().toString()))
                .andExpect(jsonPath("$.data[0].requests.length()").value(3))
                .andExpect(jsonPath("$.data[0].requests[0].id").value(recruiting.getId()))
                .andExpect(jsonPath("$.data[0].requests[0].startAt").value(tomorrowNoon.format(JSON_DATE_TIME)))
                .andExpect(jsonPath("$.data[0].requests[0].helpTypes[0]").value("OTHER"))
                .andExpect(jsonPath("$.data[0].requests[0].applyOutcome").value("MATCH"))
                .andExpect(jsonPath("$.data[0].requests[0].blockReason").value(nullValue()))
                .andExpect(jsonPath("$.data[0].requests[0].otherHelpText").doesNotExist())
                .andExpect(jsonPath("$.data[0].requests[0].memo").doesNotExist())
                .andExpect(jsonPath("$.data[0].requests[0].student").doesNotExist())
                .andExpect(jsonPath("$.data[0].requests[0].status").doesNotExist())
                .andExpect(jsonPath("$.data[0].requests[1].id").value(myMatched.getId()))
                .andExpect(jsonPath("$.data[0].requests[1].applyOutcome").value("BLOCKED"))
                .andExpect(jsonPath("$.data[0].requests[1].blockReason").value("ALREADY_APPLIED"))
                .andExpect(jsonPath("$.data[0].requests[2].id").value(overlapping.getId()))
                .andExpect(jsonPath("$.data[0].requests[2].applyOutcome").value("BLOCKED"))
                .andExpect(jsonPath("$.data[0].requests[2].blockReason").value("TIME_OVERLAP"));
    }

    @Test
    void 요청목록_기간지정_그날만() throws Exception {
        // given
        LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
        Student student = saveStudent("60231234");
        saveHelper();
        saveRequest(student, tomorrow.atTime(12, 0));
        saveRequest(student, tomorrow.plusDays(1).atTime(12, 0));
        String token = loginBearer(HELPER_LOGIN);

        // when & then
        mockMvc.perform(get(OPEN_REQUESTS).header(HttpHeaders.AUTHORIZATION, token)
                        .param("from", tomorrow.toString()).param("to", tomorrow.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].date").value(tomorrow.toString()))
                .andExpect(jsonPath("$.data[0].requests.length()").value(1));
    }

    @Test
    void 요청목록_from이to보다뒤_422() throws Exception {
        // given
        saveHelper();
        String token = loginBearer(HELPER_LOGIN);

        // when & then
        mockMvc.perform(get(OPEN_REQUESTS).header(HttpHeaders.AUTHORIZATION, token)
                        .param("from", "2026-10-13").param("to", "2026-10-12"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("HELP_REQUEST_INVALID_DATE_RANGE"));
    }

    @Test
    void 요청목록_날짜형식오류_400() throws Exception {
        // given
        saveHelper();
        String token = loginBearer(HELPER_LOGIN);

        // when & then
        mockMvc.perform(get(OPEN_REQUESTS).header(HttpHeaders.AUTHORIZATION, token).param("from", "10/12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void 요청목록_장애학생이조회_403() throws Exception {
        // given
        saveStudent("60231234");
        String token = loginBearer("60231234");

        // when & then
        mockMvc.perform(get(OPEN_REQUESTS).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
    }
}
