package com.hankkiatti.domain.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.mail.entity.MailOutbox;
import com.hankkiatti.domain.mail.entity.MailType;
import com.hankkiatti.domain.mail.repository.MailOutboxRepository;
import com.hankkiatti.domain.student.entity.DisabilityType;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 SecurityFilterChain으로 공개 경로인 비밀번호 재설정 흐름을 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordResetIntegrationTest {

    private static final String PASSWORD = "hankki!2026";
    private static final String NEW_PASSWORD = "newPass!2026";
    private static final String USER_COOKIE = "__Host-hankki_rt";
    private static final String TOKEN_MARKER = "/password-reset#token=";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private MailOutboxRepository mailOutboxRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private void saveStudent(String studentNo, boolean mustChangePassword) {
        Account account = accountRepository.save(new Account(studentNo, passwordEncoder.encode(PASSWORD),
                AccountRole.STUDENT, mustChangePassword, false));
        studentRepository.save(new Student(account, "김학생", studentNo, "010-0000-0000", "kakao",
                studentNo + "@mju.ac.kr", DisabilityType.PHYSICAL, null));
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions requestReset(String loginId) throws Exception {
        return postJson("/api/auth/password-reset/request", "{\"loginId\":\"" + loginId + "\"}");
    }

    private ResultActions verify(String token) throws Exception {
        return postJson("/api/auth/password-reset/verify", "{\"token\":\"" + token + "\"}");
    }

    private ResultActions confirm(String token, String newPassword) throws Exception {
        return postJson("/api/auth/password-reset/confirm",
                "{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}");
    }

    private MockHttpServletResponse login(String loginId, String password) throws Exception {
        return postJson("/api/auth/login", "{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}")
                .andReturn().getResponse();
    }

    private List<MailOutbox> resetMails() {
        return mailOutboxRepository.findAll().stream()
                .filter(mail -> mail.getMailType() == MailType.PASSWORD_RESET)
                .toList();
    }

    // 테스트 트랜잭션은 커밋되지 않아 메일이 발송 대기로 남아 있다 → 본문 링크에서 토큰을 꺼낸다
    private String tokenFromMail() {
        List<MailOutbox> mails = resetMails();
        assertThat(mails).hasSize(1);
        String body = mails.get(0).getBody();
        int start = body.indexOf(TOKEN_MARKER) + TOKEN_MARKER.length();
        return body.substring(start, body.indexOf('\n', start));
    }

    @Test
    void 재설정흐름_새비밀번호로로그인되고다른기기는로그아웃되고링크는한번만() throws Exception {
        // given — 첫 로그인 비밀번호 변경 전인 학생이 다른 기기에 로그인해 있다
        saveStudent("60231234", true);
        MockHttpServletResponse otherDevice = login("60231234", PASSWORD);
        String oldAccessToken = JsonPath.read(otherDevice.getContentAsString(), "$.data.accessToken");
        Cookie oldRefreshCookie = otherDevice.getCookie(USER_COOKIE);

        // when
        requestReset("60231234").andExpect(status().isOk());
        String token = tokenFromMail();
        verify(token).andExpect(status().isOk());
        confirm(token, NEW_PASSWORD).andExpect(status().isOk());

        // then — 새 비밀번호로 로그인되고 변경 필요 상태가 풀린다
        MockHttpServletResponse newLogin = login("60231234", NEW_PASSWORD);
        assertThat(newLogin.getStatus()).isEqualTo(200);
        assertThat((Boolean) JsonPath.read(newLogin.getContentAsString(), "$.data.mustChangePassword")).isFalse();
        assertThat(login("60231234", PASSWORD).getStatus()).isEqualTo(401);
        // 다른 기기의 access·refresh 토큰은 더 못 쓴다
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldAccessToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(oldRefreshCookie))
                .andExpect(status().isUnauthorized());
        // 같은 링크는 다시 못 쓴다
        verify(token)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("AUTH_PASSWORD_RESET_LINK_INVALID"));
        confirm(token, "another!2026").andExpect(status().isGone());
    }

    @Test
    void 요청_있는아이디와없는아이디는같은응답_메일은있는아이디에만() throws Exception {
        // given
        saveStudent("60231234", false);

        // when
        String existing = requestReset("60231234").andReturn().getResponse().getContentAsString();
        String unknown = requestReset("99999999").andReturn().getResponse().getContentAsString();

        // then
        assertThat(existing).isEqualTo(unknown);
        assertThat(resetMails()).hasSize(1);
        assertThat(resetMails().get(0).getRecipient()).isEqualTo("60231234@mju.ac.kr");
    }

    @Test
    void 요청_10분안에다시요청하면메일은한통만_처음링크가계속유효() throws Exception {
        // given
        saveStudent("60231234", false);
        requestReset("60231234");
        String firstToken = tokenFromMail();

        // when
        requestReset("60231234").andExpect(status().isOk());

        // then
        assertThat(resetMails()).hasSize(1);
        verify(firstToken).andExpect(status().isOk());
    }

    @Test
    void 공개경로에만료된토큰이붙어와도_요청성공() throws Exception {
        // when & then
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired-or-broken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"99999999\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 완료_비밀번호규칙위반_422이고링크는그대로유효() throws Exception {
        // given
        saveStudent("60231234", false);
        requestReset("60231234");
        String token = tokenFromMail();

        // when & then
        confirm(token, "short").andExpect(status().isUnprocessableContent());
        verify(token).andExpect(status().isOk());
    }
}
