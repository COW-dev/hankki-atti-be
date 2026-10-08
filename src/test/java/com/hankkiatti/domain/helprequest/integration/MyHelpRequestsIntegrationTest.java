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
 * 실제 SecurityFilterChain으로 내 신청 조회를 확인한다. 여러 상태의 신청과 지원을 직접 넣는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MyHelpRequestsIntegrationTest {

    private static final String PASSWORD = "hankki!2026";
    private static final String MY_REQUESTS = "/api/help-requests/me";
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

    private String loginBearer(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private HelpRequest saveRequest(Student student, LocalDateTime startAt) {
        return helpRequestRepository.save(new HelpRequest(student, startAt, Set.of(HelpType.SERVING), null, "출입구"));
    }

    private Application saveMatchedApplication(HelpRequest request, Helper helper, LocalDateTime matchedAt) {
        Application application = new Application(request, helper, matchedAt);
        application.match(matchedAt);
        return applicationRepository.save(application);
    }

    @Test
    void 내신청조회_다가오는가까운순과지난최근순_매칭건만도우미이름과카톡ID() throws Exception {
        // given
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.HOURS);
        Student me = saveStudent("60231234");
        Student other = saveStudent("60239999");
        Helper helper = helperRepository.save(
                TestProfiles.helper(saveAccount("helper@mju.ac.kr", AccountRole.HELPER), "60230001"));

        HelpRequest recruiting = saveRequest(me, now.plusDays(3));
        HelpRequest matched = saveRequest(me, now.plusDays(1));
        matched.match(now.minusHours(1));
        saveMatchedApplication(matched, helper, now.minusHours(1));
        HelpRequest completed = saveRequest(me, now.minusHours(3));
        completed.match(now.minusDays(1));
        completed.complete(now.minusHours(2));
        Application completedApplication = saveMatchedApplication(completed, helper, now.minusDays(1));
        completedApplication.complete();
        HelpRequest failed = saveRequest(me, now.minusDays(2));
        failed.fail();
        saveRequest(other, now.plusDays(1));
        String token = loginBearer("60231234");

        // when & then
        mockMvc.perform(get(MY_REQUESTS).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upcoming.length()").value(2))
                .andExpect(jsonPath("$.data.upcoming[0].id").value(matched.getId()))
                .andExpect(jsonPath("$.data.upcoming[0].status").value("MATCHED"))
                .andExpect(jsonPath("$.data.upcoming[0].helper.name").value("이도움"))
                .andExpect(jsonPath("$.data.upcoming[0].helper.kakaoId").value("kakao_helper"))
                .andExpect(jsonPath("$.data.upcoming[0].helper.phone").doesNotExist())
                .andExpect(jsonPath("$.data.upcoming[1].id").value(recruiting.getId()))
                .andExpect(jsonPath("$.data.upcoming[1].helper").value(nullValue()))
                .andExpect(jsonPath("$.data.upcoming[1].memo").value("출입구"))
                .andExpect(jsonPath("$.data.past.length()").value(2))
                .andExpect(jsonPath("$.data.past[0].id").value(completed.getId()))
                .andExpect(jsonPath("$.data.past[0].helper.name").value("이도움"))
                .andExpect(jsonPath("$.data.past[0].noShowReportable").value(true))
                .andExpect(jsonPath("$.data.past[0].noShowDeadline").value(now.plusHours(22).format(JSON_DATE_TIME)))
                .andExpect(jsonPath("$.data.past[1].id").value(failed.getId()))
                .andExpect(jsonPath("$.data.past[1].status").value("FAILED"))
                .andExpect(jsonPath("$.data.past[1].noShowReportable").value(false));
    }

    @Test
    void 신청이없으면_빈두목록() throws Exception {
        // given
        saveStudent("60231234");
        String token = loginBearer("60231234");

        // when & then
        mockMvc.perform(get(MY_REQUESTS).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upcoming").isEmpty())
                .andExpect(jsonPath("$.data.past").isEmpty());
    }

    @Test
    void 도우미가조회_403() throws Exception {
        // given
        helperRepository.save(TestProfiles.helper(saveAccount("helper@mju.ac.kr", AccountRole.HELPER), "60230001"));
        String token = loginBearer("helper@mju.ac.kr");

        // when & then
        mockMvc.perform(get(MY_REQUESTS).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
    }
}
