package com.hankkiatti.domain.application.integration;

import static org.hamcrest.Matchers.nullValue;
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
 * 실제 SecurityFilterChain으로 지원 API를 확인한다. 예비 응답에 장애학생 정보가 없는지까지 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApplicationIntegrationTest {

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

    private Student saveStudent() {
        return studentRepository.save(TestProfiles.student(saveAccount("60231234", AccountRole.STUDENT)));
    }

    private void saveHelper(String email, String studentNo) {
        helperRepository.save(TestProfiles.helper(saveAccount(email, AccountRole.HELPER), studentNo));
    }

    private String loginBearer(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private HelpRequest saveRequest(Student student) {
        LocalDateTime tomorrowNoon = LocalDateTime.now(clock).plusDays(1).truncatedTo(ChronoUnit.DAYS).withHour(12);
        return helpRequestRepository.save(new HelpRequest(student, tomorrowNoon, Set.of(HelpType.SERVING), "식판 반납", "메모"));
    }

    private static String applyPath(Long helpRequestId) {
        return "/api/help-requests/" + helpRequestId + "/applications";
    }

    @Test
    void 지원_첫도우미는바로매칭되고학생이름카톡ID_두번째는예비1번이고학생정보없음() throws Exception {
        // given
        HelpRequest request = saveRequest(saveStudent());
        saveHelper("first@mju.ac.kr", "60230001");
        saveHelper("second@mju.ac.kr", "60230002");

        // when & then
        mockMvc.perform(post(applyPath(request.getId())).header(HttpHeaders.AUTHORIZATION, loginBearer("first@mju.ac.kr")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("MATCHED"))
                .andExpect(jsonPath("$.data.helpRequestId").value(request.getId()))
                .andExpect(jsonPath("$.data.waitingOrder").value(nullValue()))
                .andExpect(jsonPath("$.data.student.name").value("김한끼"))
                .andExpect(jsonPath("$.data.student.kakaoId").value("kakao_student"))
                .andExpect(jsonPath("$.data.student.phone").doesNotExist())
                .andExpect(jsonPath("$.data.student.disabilityType").doesNotExist())
                .andExpect(jsonPath("$.data.memo").doesNotExist())
                .andExpect(jsonPath("$.data.otherHelpText").doesNotExist());

        mockMvc.perform(post(applyPath(request.getId())).header(HttpHeaders.AUTHORIZATION, loginBearer("second@mju.ac.kr")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.waitingOrder").value(1))
                .andExpect(jsonPath("$.data.student").value(nullValue()));
    }

    @Test
    void 지원_같은신청다시지원_409_ALREADY_APPLIED() throws Exception {
        // given
        HelpRequest request = saveRequest(saveStudent());
        saveHelper("first@mju.ac.kr", "60230001");
        String token = loginBearer("first@mju.ac.kr");
        mockMvc.perform(post(applyPath(request.getId())).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isCreated());

        // when & then
        mockMvc.perform(post(applyPath(request.getId())).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_ALREADY_APPLIED"));
    }

    @Test
    void 지원_장애학생이지원_403() throws Exception {
        // given
        HelpRequest request = saveRequest(saveStudent());

        // when & then
        mockMvc.perform(post(applyPath(request.getId())).header(HttpHeaders.AUTHORIZATION, loginBearer("60231234")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
    }

    @Test
    void 지원_없는신청_404() throws Exception {
        // given
        saveHelper("first@mju.ac.kr", "60230001");

        // when & then
        mockMvc.perform(post(applyPath(999_999L)).header(HttpHeaders.AUTHORIZATION, loginBearer("first@mju.ac.kr")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HELP_REQUEST_NOT_FOUND"));
    }
}
