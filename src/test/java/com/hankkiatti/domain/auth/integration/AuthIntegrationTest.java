package com.hankkiatti.domain.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 SecurityFilterChain으로 인증·인가 흐름을 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTest {

    private static final String PASSWORD = "hankki!2026";
    private static final String USER_COOKIE = "__Host-hankki_rt";
    private static final String ADMIN_COOKIE = "__Host-hankki_admin_rt";

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

    private MockHttpServletResponse login(String path, String loginId, String password) throws Exception {
        return mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse();
    }

    private String accessTokenOf(MockHttpServletResponse response) throws Exception {
        return JsonPath.read(response.getContentAsString(), "$.data.accessToken");
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    @Test
    void 사용자로그인후내정보조회_성공() throws Exception {
        // given
        saveAccount("60231234", AccountRole.STUDENT, false);

        // when
        MockHttpServletResponse loginResponse = login("/api/auth/login", "60231234", PASSWORD);

        // then
        assertThat(loginResponse.getStatus()).isEqualTo(200);
        Cookie refreshCookie = loginResponse.getCookie(USER_COOKIE);
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(accessTokenOf(loginResponse))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value("60231234"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"));
    }

    @Test
    void 토큰없이보호된API_401() throws Exception {
        // when & then
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.resultType").value("FAIL"))
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value(AuthErrorType.UNAUTHENTICATED.getMessage()));
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 로그인실패_틀린비밀번호와없는아이디는같은응답() throws Exception {
        // given
        saveAccount("60231234", AccountRole.STUDENT, false);

        // when
        MockHttpServletResponse wrongPassword = login("/api/auth/login", "60231234", "wrong!1234");
        MockHttpServletResponse unknownId = login("/api/auth/login", "99999999", "wrong!1234");

        // then
        assertThat(wrongPassword.getStatus()).isEqualTo(401);
        assertThat(unknownId.getStatus()).isEqualTo(401);
        assertThat(wrongPassword.getContentAsString()).isEqualTo(unknownId.getContentAsString());
    }

    @Test
    void 공개경로에만료된토큰이붙어와도_로그인성공() throws Exception {
        // given
        saveAccount("60231234", AccountRole.STUDENT, false);

        // when & then
        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired-or-broken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"60231234\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 비밀번호변경필요계정_변경전에는403_변경후에는새토큰으로통과() throws Exception {
        // given
        saveAccount("60231234", AccountRole.STUDENT, true);
        MockHttpServletResponse loginResponse = login("/api/auth/login", "60231234", PASSWORD);
        String oldToken = accessTokenOf(loginResponse);
        assertThat((Boolean) JsonPath.read(loginResponse.getContentAsString(), "$.data.mustChangePassword")).isTrue();

        // when & then — 변경 전
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(oldToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PASSWORD_CHANGE_REQUIRED"))
                .andExpect(jsonPath("$.message").value(AuthErrorType.PASSWORD_CHANGE_REQUIRED.getMessage()));

        // when — 변경
        MockHttpServletResponse changeResponse = mockMvc.perform(patch("/api/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(oldToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"newPass!2026\"}"))
                .andReturn().getResponse();

        // then — 새 토큰은 통과, 옛 토큰은 무효
        assertThat(changeResponse.getStatus()).isEqualTo(200);
        assertThat(changeResponse.getCookie(USER_COOKIE)).isNotNull();
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(accessTokenOf(changeResponse))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(oldToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 비밀번호변경_규칙위반_422() throws Exception {
        // given
        saveAccount("60231234", AccountRole.STUDENT, true);
        String token = accessTokenOf(login("/api/auth/login", "60231234", PASSWORD));

        // when & then
        mockMvc.perform(patch("/api/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void 사용자토큰으로관리자API_403_관리자토큰으로사용자API_403() throws Exception {
        // given
        saveAccount("60231234", AccountRole.STUDENT, false);
        Account adminAccount = saveAccount("center01", AccountRole.ADMIN, false);
        adminRepository.save(new Admin(adminAccount, "김센터", AdminGrade.FULL));
        String userToken = accessTokenOf(login("/api/auth/login", "60231234", PASSWORD));
        String adminToken = accessTokenOf(login("/api/admin/auth/login", "center01", PASSWORD));

        // when & then
        mockMvc.perform(get("/api/admin/me").header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value(AuthErrorType.ACCESS_DENIED.getMessage()));
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/me").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("김센터"))
                .andExpect(jsonPath("$.data.grade").value("FULL"));
    }

    @Test
    void 관리자계정으로사용자로그인_실패() throws Exception {
        // given
        Account adminAccount = saveAccount("center01", AccountRole.ADMIN, false);
        adminRepository.save(new Admin(adminAccount, "김센터", AdminGrade.FULL));

        // when & then
        assertThat(login("/api/auth/login", "center01", PASSWORD).getStatus()).isEqualTo(401);
    }

    @Test
    void refresh_새토큰발급_쓴토큰재사용하면계정토큰모두폐기() throws Exception {
        // given
        saveAccount("60231234", AccountRole.STUDENT, false);
        Cookie firstCookie = login("/api/auth/login", "60231234", PASSWORD).getCookie(USER_COOKIE);

        // when — 정상 회전
        MockHttpServletResponse refreshed = mockMvc.perform(post("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .cookie(firstCookie))
                .andReturn().getResponse();

        // then
        assertThat(refreshed.getStatus()).isEqualTo(200);
        Cookie secondCookie = refreshed.getCookie(USER_COOKIE);
        assertThat(secondCookie.getValue()).isNotEqualTo(firstCookie.getValue());
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(accessTokenOf(refreshed))))
                .andExpect(status().isOk());

        // when & then — 이미 쓴 토큰 재사용 → 실패, 새 토큰도 함께 폐기
        mockMvc.perform(post("/api/auth/refresh").cookie(firstCookie))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(secondCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_허용되지않은Origin_CORS필터가403() throws Exception {
        // given
        saveAccount("60231234", AccountRole.STUDENT, false);
        Cookie cookie = login("/api/auth/login", "60231234", PASSWORD).getCookie(USER_COOKIE);

        // when & then
        mockMvc.perform(post("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "https://other-club.bluerack.org")
                        .cookie(cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void refresh_쿠키없음_401() throws Exception {
        // when & then
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN"))
                .andExpect(jsonPath("$.message").value(AuthErrorType.INVALID_REFRESH_TOKEN.getMessage()));
    }

    @Test
    void logout_쿠키삭제하고같은토큰으로재발급불가() throws Exception {
        // given
        saveAccount("60231234", AccountRole.STUDENT, false);
        Cookie cookie = login("/api/auth/login", "60231234", PASSWORD).getCookie(USER_COOKIE);

        // when
        MockHttpServletResponse logout = mockMvc.perform(post("/api/auth/logout").cookie(cookie))
                .andReturn().getResponse();

        // then
        assertThat(logout.getStatus()).isEqualTo(200);
        assertThat(logout.getCookie(USER_COOKIE).getMaxAge()).isZero();
        mockMvc.perform(post("/api/auth/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 관리자refresh와로그아웃_관리자쿠키로동작() throws Exception {
        // given
        Account adminAccount = saveAccount("center01", AccountRole.ADMIN, false);
        adminRepository.save(new Admin(adminAccount, "김센터", AdminGrade.LIMITED));
        Cookie cookie = login("/api/admin/auth/login", "center01", PASSWORD).getCookie(ADMIN_COOKIE);

        // when
        MockHttpServletResponse refreshed = mockMvc.perform(post("/api/admin/auth/refresh").cookie(cookie))
                .andReturn().getResponse();

        // then
        assertThat(refreshed.getStatus()).isEqualTo(200);
        mockMvc.perform(post("/api/admin/auth/logout").cookie(refreshed.getCookie(ADMIN_COOKIE)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/auth/refresh").cookie(refreshed.getCookie(ADMIN_COOKIE)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 비활성화된계정의토큰_바로401() throws Exception {
        // given
        Account student = saveAccount("60231234", AccountRole.STUDENT, false);
        String token = accessTokenOf(login("/api/auth/login", "60231234", PASSWORD));

        // when
        student.deactivate(LocalDateTime.now());
        accountRepository.flush();

        // then
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
        assertThat(login("/api/auth/login", "60231234", PASSWORD).getStatus()).isEqualTo(403);
    }
}
