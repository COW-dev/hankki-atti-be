package com.hankkiatti.domain.application.integration;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 SecurityFilterChain으로 도우미 매칭 취소 API를 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HelperCancelIntegrationTest {

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
    private ApplicationRepository applicationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    private Account saveAccount(String loginId, AccountRole role) {
        return accountRepository.save(new Account(loginId, passwordEncoder.encode(PASSWORD), role, false, false));
    }

    private Helper saveHelper(String email, String studentNo) {
        return helperRepository.save(TestProfiles.helper(saveAccount(email, AccountRole.HELPER), studentNo));
    }

    // 장애학생의 내일 12:00 신청에 helper가 매칭돼 있다
    private Application saveMatched(Helper helper) {
        Student student = studentRepository.save(TestProfiles.student(saveAccount("60231234", AccountRole.STUDENT)));
        LocalDateTime now = LocalDateTime.now(clock);
        HelpRequest request = new HelpRequest(student, now.plusDays(1).truncatedTo(ChronoUnit.DAYS).withHour(12),
                Set.of(HelpType.SERVING), null, null);
        request.match(now);
        helpRequestRepository.save(request);
        Application application = new Application(request, helper, now);
        application.match(now);
        return applicationRepository.save(application);
    }

    private String loginBearer(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private ResultActions cancel(Long applicationId, String token, String body) throws Exception {
        return mockMvc.perform(post("/api/applications/" + applicationId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void 취소_정상_취소된지원을돌려주고장애학생정보는없다() throws Exception {
        // given
        Application mine = saveMatched(saveHelper("first@mju.ac.kr", "60230001"));

        // when & then
        cancel(mine.getId(), loginBearer("first@mju.ac.kr"), "{\"reason\":\"ILLNESS\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationId").value(mine.getId()))
                .andExpect(jsonPath("$.data.status").value("HELPER_CANCELED"))
                .andExpect(jsonPath("$.data.cancelReason").value("ILLNESS"))
                .andExpect(jsonPath("$.data.student").doesNotExist())
                .andExpect(jsonPath("$.data.afterAction").doesNotExist());
    }

    @Test
    void 취소_사유없음_422_사유값오류_400() throws Exception {
        // given
        Application mine = saveMatched(saveHelper("first@mju.ac.kr", "60230001"));
        String token = loginBearer("first@mju.ac.kr");

        // when & then
        cancel(mine.getId(), token, "{}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
        cancel(mine.getId(), token, "{\"reason\":\"BORED\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void 취소_기타인데내용없음_422() throws Exception {
        // given
        Application mine = saveMatched(saveHelper("first@mju.ac.kr", "60230001"));

        // when & then
        cancel(mine.getId(), loginBearer("first@mju.ac.kr"), "{\"reason\":\"OTHER\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("APPLICATION_CANCEL_REASON_DETAIL_REQUIRED"));
    }

    @Test
    void 취소_다른도우미의지원_404() throws Exception {
        // given
        Application someoneElses = saveMatched(saveHelper("first@mju.ac.kr", "60230001"));
        saveHelper("second@mju.ac.kr", "60230002");

        // when & then
        cancel(someoneElses.getId(), loginBearer("second@mju.ac.kr"), "{\"reason\":\"ILLNESS\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
    }
}
