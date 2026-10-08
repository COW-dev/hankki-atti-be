package com.hankkiatti.domain.account.integration;

import static org.hamcrest.Matchers.nullValue;
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
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.support.TestHelpRequests;
import com.hankkiatti.support.TestProfiles;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
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
 * 실제 SecurityFilterChain으로 내 정보 조회·설정 변경·연락처 수정 API를 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MeIntegrationTest {

    private static final String PASSWORD = "hankki!2026";
    private static final String HELPER_EMAIL = "helper@mju.ac.kr";

    @Autowired
    private MockMvc mockMvc;

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
    private PasswordEncoder passwordEncoder;

    private Account saveAccount(String loginId, AccountRole role, boolean mustChangePassword) {
        return accountRepository.save(
                new Account(loginId, passwordEncoder.encode(PASSWORD), role, mustChangePassword, false));
    }

    private Student saveStudent(String studentNo, boolean mustChangePassword) {
        return studentRepository.save(
                TestProfiles.student(saveAccount(studentNo, AccountRole.STUDENT, mustChangePassword)));
    }

    private Helper saveHelper() {
        return helperRepository.save(
                TestProfiles.helper(saveAccount(HELPER_EMAIL, AccountRole.HELPER, false), "60230001"));
    }

    private String loginBearer(String path, String loginId) throws Exception {
        String body = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private String settingsBody(Object accessibilityMode) {
        return "{\"accessibilityMode\":" + accessibilityMode + "}";
    }

    private ResultActions updateContact(String token, String phone, String kakaoId) throws Exception {
        return mockMvc.perform(patch("/api/me/contact")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\",\"kakaoId\":\"" + kakaoId + "\"}"));
    }

    @Test
    void 장애학생내정보조회_프로필포함_도우미정보와장애정보는없음() throws Exception {
        // given
        saveStudent("60231234", false);
        String token = loginBearer("/api/auth/login", "60231234");

        // when & then
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.name").value("김한끼"))
                .andExpect(jsonPath("$.data.studentNo").value("60231234"))
                .andExpect(jsonPath("$.data.phone").value("010-0000-0000"))
                .andExpect(jsonPath("$.data.kakaoId").value("kakao_student"))
                .andExpect(jsonPath("$.data.helper").value(nullValue()))
                .andExpect(jsonPath("$.data.disabilityType").doesNotExist())
                .andExpect(jsonPath("$.data.specialNote").doesNotExist());
    }

    @Test
    void 도우미내정보조회_봉사시간은기록된지원만합산() throws Exception {
        // given — 이용 완료 2건(1.0씩) + 매칭만 된 1건(기록 없음)
        Helper helper = saveHelper();
        Student student = saveStudent("60231234", false);
        LocalDateTime lunch = LocalDateTime.of(2026, 10, 6, 12, 0);
        for (int day = 0; day < 3; day++) {
            HelpRequest request = helpRequestRepository.save(TestHelpRequests.request(student, lunch.plusDays(day)));
            Application application = new Application(request, helper, lunch.minusDays(1));
            application.match(lunch.minusDays(1));
            if (day < 2) {
                application.complete();
            }
            applicationRepository.save(application);
        }
        String token = loginBearer("/api/auth/login", HELPER_EMAIL);

        // when & then
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("HELPER"))
                .andExpect(jsonPath("$.data.loginId").value(HELPER_EMAIL))
                .andExpect(jsonPath("$.data.studentNo").value("60230001"))
                .andExpect(jsonPath("$.data.helper.attiMember").value(true))
                .andExpect(jsonPath("$.data.helper.volunteerHours").value(2.0));
    }

    @Test
    void 도우미연락처수정_하이픈형식으로저장되고다시조회해도반영() throws Exception {
        // given
        saveHelper();
        String token = loginBearer("/api/auth/login", HELPER_EMAIL);

        // when
        updateContact(token, "01098765432", "new_kakao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("010-9876-5432"))
                .andExpect(jsonPath("$.data.kakaoId").value("new_kakao"))
                .andExpect(jsonPath("$.data.helper.volunteerHours").value(0.0));

        // then
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.data.phone").value("010-9876-5432"))
                .andExpect(jsonPath("$.data.kakaoId").value("new_kakao"));
    }

    @Test
    void 장애학생이연락처수정_403() throws Exception {
        // given
        saveStudent("60231234", false);
        String token = loginBearer("/api/auth/login", "60231234");

        // when & then
        updateContact(token, "010-9876-5432", "new_kakao")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
    }

    @Test
    void 연락처수정_전화번호형식오류와카톡ID공백_422() throws Exception {
        // given
        saveHelper();
        String token = loginBearer("/api/auth/login", HELPER_EMAIL);

        // when & then
        updateContact(token, "02-123-4567", "new_kakao")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
        updateContact(token, "010-9876-5432", "new kakao")
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void 접근성모드저장_응답과다시조회한내정보에반영() throws Exception {
        // given
        saveStudent("60231234", false);
        String token = loginBearer("/api/auth/login", "60231234");

        // when
        mockMvc.perform(patch("/api/me/settings")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settingsBody(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessibilityMode").value(true));

        // then
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessibilityMode").value(true));
    }

    @Test
    void 접근성모드값없음_422() throws Exception {
        // given
        saveHelper();
        String token = loginBearer("/api/auth/login", HELPER_EMAIL);

        // when & then
        mockMvc.perform(patch("/api/me/settings")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settingsBody(null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
    }

    @Test
    void 비밀번호변경필요계정과관리자토큰_403() throws Exception {
        // given
        saveStudent("60231234", true);
        Account adminAccount = saveAccount("center01", AccountRole.ADMIN, false);
        adminRepository.save(new Admin(adminAccount, "김센터", AdminGrade.FULL));
        String mustChangeToken = loginBearer("/api/auth/login", "60231234");
        String adminToken = loginBearer("/api/admin/auth/login", "center01");

        // when & then
        mockMvc.perform(patch("/api/me/settings")
                        .header(HttpHeaders.AUTHORIZATION, mustChangeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settingsBody(true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(patch("/api/me/settings")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settingsBody(true)))
                .andExpect(status().isForbidden());
        updateContact(adminToken, "010-9876-5432", "new_kakao")
                .andExpect(status().isForbidden());
    }
}
