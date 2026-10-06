package com.hankkiatti.domain.helper.integration;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 SecurityFilterChain으로 공개 경로인 도우미 회원가입을 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HelperSignupIntegrationTest {

    private static final String SIGNUP = "/api/helpers/signup";
    private static final String PASSWORD = "hankki!2026";

    @Autowired
    private MockMvc mockMvc;

    private String body(String email, String studentNo, String kakaoId, boolean guideConfirmed) {
        return """
                {"name":"이도움","studentNo":"%s","email":"%s","phone":"010-1234-5678","kakaoId":"%s",
                 "password":"%s","attiMember":true,"guideConfirmed":%s}
                """.formatted(studentNo, email, kakaoId, PASSWORD, guideConfirmed);
    }

    private ResultActions signup(String body) throws Exception {
        return mockMvc.perform(post(SIGNUP).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String loginId) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"));
    }

    @Test
    void 가입하면_대소문자상관없이이메일로바로로그인() throws Exception {
        // when
        signup(body("New.Helper@MJU.ac.kr", "60230001", "hankki_helper", true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.loginId").value("new.helper@mju.ac.kr"));

        // then
        login("new.helper@mju.ac.kr")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("HELPER"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));
        login(" NEW.HELPER@mju.ac.kr ")
                .andExpect(status().isOk());
    }

    @Test
    void 로그인없이가입_만료되거나잘못된토큰이붙어와도_가입성공() throws Exception {
        mockMvc.perform(post(SIGNUP)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("helper@mju.ac.kr", "60230001", "hankki_helper", true)))
                .andExpect(status().isCreated());
    }

    @Test
    void 이미가입된이메일과학번_409() throws Exception {
        // given
        signup(body("helper@mju.ac.kr", "60230001", "hankki_helper", true)).andExpect(status().isCreated());

        // when & then
        signup(body("HELPER@mju.ac.kr", "60230002", "other_id", true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HELPER_DUPLICATE_EMAIL"));
        signup(body("other@mju.ac.kr", "60230001", "other_id", true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HELPER_DUPLICATE_STUDENT_NO"));
    }

    @Test
    void 입력값검증실패_422() throws Exception {
        // 학번 7자리
        signup(body("helper@mju.ac.kr", "6023000", "hankki_helper", true))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
        // 카톡 ID에 공백
        signup(body("helper@mju.ac.kr", "60230001", "hankki helper", true))
                .andExpect(status().isUnprocessableContent());
        // 안내 자료를 다 확인하지 않음
        signup(body("helper@mju.ac.kr", "60230001", "hankki_helper", false))
                .andExpect(status().isUnprocessableContent());
        // 학교 이메일이 아님
        signup(body("helper@gmail.com", "60230001", "hankki_helper", true))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value(containsString("@mju.ac.kr")));
    }
}
