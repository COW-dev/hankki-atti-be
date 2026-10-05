package com.hankkiatti.domain.account.integration;

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
import com.jayway.jsonpath.JsonPath;
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
 * 실제 SecurityFilterChain으로 내 설정 변경 API를 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MeIntegrationTest {

    private static final String PASSWORD = "hankki!2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Account saveAccount(String loginId, AccountRole role, boolean mustChangePassword) {
        return accountRepository.save(
                new Account(loginId, passwordEncoder.encode(PASSWORD), role, mustChangePassword, false));
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

    @Test
    void 접근성모드저장_응답과다시조회한내정보에반영() throws Exception {
        // given
        saveAccount("60231234", AccountRole.STUDENT, false);
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
        saveAccount("helper@mju.ac.kr", AccountRole.HELPER, false);
        String token = loginBearer("/api/auth/login", "helper@mju.ac.kr");

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
        saveAccount("60231234", AccountRole.STUDENT, true);
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
    }
}
