package com.hankkiatti.domain.helprequest.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
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
 * 실제 SecurityFilterChain으로 신청 철회를 확인한다. 신청은 직접 넣는다 (시작 시각 규칙은 생성 API 테스트가 본다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HelpRequestWithdrawIntegrationTest {

    private static final String PASSWORD = "hankki!2026";

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    private Account saveAccount(String loginId, AccountRole role) {
        return accountRepository.save(new Account(loginId, passwordEncoder.encode(PASSWORD), role, false, false));
    }

    private Student saveStudent(String studentNo) {
        return studentRepository.save(TestProfiles.student(saveAccount(studentNo, AccountRole.STUDENT)));
    }

    private HelpRequest saveRequest(Student student, LocalDateTime startAt) {
        return helpRequestRepository.save(new HelpRequest(student, startAt, Set.of(HelpType.SERVING), null, null));
    }

    private String loginBearer(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private ResultActions withdraw(String token, Long helpRequestId) throws Exception {
        return mockMvc.perform(post("/api/help-requests/" + helpRequestId + "/withdraw")
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    @Test
    void 모집중신청철회_철회된신청을돌려주고내신청의지난신청으로이동() throws Exception {
        // given
        Student me = saveStudent("60231234");
        HelpRequest request = saveRequest(me, LocalDateTime.now(clock).plusDays(1));
        String token = loginBearer("60231234");

        // when
        withdraw(token, request.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(request.getId()))
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.helper").doesNotExist());

        // then
        mockMvc.perform(get("/api/help-requests/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.data.upcoming").isEmpty())
                .andExpect(jsonPath("$.data.past[0].id").value(request.getId()))
                .andExpect(jsonPath("$.data.past[0].status").value("CANCELED"));
        withdraw(token, request.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HELP_REQUEST_INVALID_STATUS"));
    }

    @Test
    void 식사가시작된신청철회_409() throws Exception {
        // given — 스케줄러가 매칭 실패로 바꾸기 전, 아직 모집 중인 시작된 신청
        Student me = saveStudent("60231234");
        HelpRequest started = saveRequest(me, LocalDateTime.now(clock).minusMinutes(1));
        String token = loginBearer("60231234");

        // when & then
        withdraw(token, started.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HELP_REQUEST_INVALID_STATUS"));
    }

    @Test
    void 남의신청과없는신청_404() throws Exception {
        // given
        saveStudent("60231234");
        HelpRequest othersRequest = saveRequest(saveStudent("60239999"), LocalDateTime.now(clock).plusDays(1));
        String token = loginBearer("60231234");

        // when & then
        withdraw(token, othersRequest.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HELP_REQUEST_NOT_FOUND"));
        withdraw(token, othersRequest.getId() + 1000)
                .andExpect(status().isNotFound());
    }

    @Test
    void 도우미가철회_403() throws Exception {
        // given
        HelpRequest request = saveRequest(saveStudent("60231234"), LocalDateTime.now(clock).plusDays(1));
        helperRepository.save(TestProfiles.helper(saveAccount("helper@mju.ac.kr", AccountRole.HELPER), "60230001"));
        String token = loginBearer("helper@mju.ac.kr");

        // when & then
        withdraw(token, request.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
    }
}
