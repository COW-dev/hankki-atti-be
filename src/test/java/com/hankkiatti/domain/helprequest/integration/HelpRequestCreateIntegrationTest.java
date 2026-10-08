package com.hankkiatti.domain.helprequest.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.helprequest.service.HelpRequestSchedule;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.support.TestProfiles;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.BeforeEach;
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
 * 실제 SecurityFilterChain과 서버 시각으로 도우미 신청을 확인한다. 신청 시각은 지금 고를 수 있는 날짜 가운데 하나를 쓴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HelpRequestCreateIntegrationTest {

    private static final String PASSWORD = "hankki!2026";
    private static final String STUDENT_NO = "60231234";
    private static final String HELP_REQUESTS = "/api/help-requests";
    // 응답 JSON 형식 (LocalDateTime.toString()은 0초를 생략한다)
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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private HelpRequestSchedule helpRequestSchedule;

    @Autowired
    private Clock clock;

    // 오늘이 아닌, 점심 시각이 모두 남은 날의 12:00 (테스트 도중 시각이 지나지 않게)
    private LocalDateTime bookableNoon;

    @BeforeEach
    void setUp() {
        LocalDate today = LocalDate.now(clock);
        bookableNoon = helpRequestSchedule.bookableStartTimes(LocalDateTime.now(clock)).stream()
                .filter(startAt -> startAt.toLocalDate().isAfter(today) && startAt.toLocalTime().equals(LocalTime.NOON))
                .findFirst()
                .orElseThrow();
    }

    private String loginBearer(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private String studentBearer() throws Exception {
        Account account = accountRepository.save(
                new Account(STUDENT_NO, passwordEncoder.encode(PASSWORD), AccountRole.STUDENT, false, false));
        studentRepository.save(TestProfiles.student(account));
        return loginBearer(STUDENT_NO);
    }

    private ResultActions create(String token, String body) throws Exception {
        return mockMvc.perform(post(HELP_REQUESTS)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String body(LocalDateTime startAt, String helpTypes) {
        return "{\"startAt\":\"" + startAt + "\",\"helpTypes\":" + helpTypes + "}";
    }

    @Test
    void 장애학생신청_201이고모집중으로저장() throws Exception {
        // given
        String token = studentBearer();

        // when
        String response = create(token, "{\"startAt\":\"" + bookableNoon + "\",\"helpTypes\":[\"OTHER\",\"SERVING\"],"
                + "\"otherHelpText\":\"식판 반납\",\"memo\":\"출입구에서 기다릴게요\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("RECRUITING"))
                .andExpect(jsonPath("$.data.endAt").value(bookableNoon.plusHours(1).format(JSON_DATE_TIME)))
                .andExpect(jsonPath("$.data.helpTypes[0]").value("SERVING"))
                .andExpect(jsonPath("$.data.helpTypes[1]").value("OTHER"))
                .andReturn().getResponse().getContentAsString();

        // then
        Integer id = JsonPath.read(response, "$.data.id");
        HelpRequest saved = helpRequestRepository.findById(id.longValue()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(HelpRequestStatus.RECRUITING);
        assertThat(saved.getStartAt()).isEqualTo(bookableNoon);
        assertThat(saved.getStudent().getStudentNo()).isEqualTo(STUDENT_NO);
        assertThat(saved.getMemo()).isEqualTo("출입구에서 기다릴게요");
    }

    @Test
    void 겹치는시간신청_409_끝시각에시작하는신청은201() throws Exception {
        // given
        String token = studentBearer();
        create(token, body(bookableNoon, "[\"SERVING\"]")).andExpect(status().isCreated());

        // when & then
        create(token, body(bookableNoon.plusMinutes(30), "[\"SERVING\"]"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HELP_REQUEST_TIME_OVERLAP"));
        create(token, body(bookableNoon.plusHours(1), "[\"SERVING\"]"))
                .andExpect(status().isCreated());
    }

    @Test
    void 고를수없는시각_422() throws Exception {
        // given
        String token = studentBearer();

        // when & then — 어제, 30분 단위 아님
        create(token, body(bookableNoon.minusDays(8), "[\"SERVING\"]"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("HELP_REQUEST_START_TIME_NOT_AVAILABLE"));
        create(token, body(bookableNoon.plusMinutes(15), "[\"SERVING\"]"))
                .andExpect(jsonPath("$.code").value("HELP_REQUEST_START_TIME_NOT_AVAILABLE"));
    }

    @Test
    void 도움유형없음과기타내용없음_422() throws Exception {
        // given
        String token = studentBearer();

        // when & then
        create(token, body(bookableNoon, "[]"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
        create(token, body(bookableNoon, "[\"OTHER\"]"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("HELP_REQUEST_OTHER_HELP_TEXT_REQUIRED"));
    }

    @Test
    void 도우미가신청_403() throws Exception {
        // given
        Account account = accountRepository.save(new Account("helper@mju.ac.kr", passwordEncoder.encode(PASSWORD),
                AccountRole.HELPER, false, false));
        helperRepository.save(TestProfiles.helper(account, "60230001"));
        String token = loginBearer("helper@mju.ac.kr");

        // when & then
        create(token, body(bookableNoon, "[\"SERVING\"]"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
    }
}
