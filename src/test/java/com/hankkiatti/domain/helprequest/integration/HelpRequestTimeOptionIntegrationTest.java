package com.hankkiatti.domain.helprequest.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.helprequest.service.HelpRequestSchedule;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
 * 실제 SecurityFilterChain과 서버 시각으로 시작 시각 선택지를 확인한다. 시각을 고정하지 않으므로 응답이 규칙을 지키는지 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HelpRequestTimeOptionIntegrationTest {

    private static final String PASSWORD = "hankki!2026";
    private static final String TIME_OPTIONS = "/api/help-requests/time-options";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private HelpRequestSchedule helpRequestSchedule;

    @Autowired
    private Clock clock;

    private String loginBearer(String loginId) throws Exception {
        accountRepository.save(new Account(loginId, passwordEncoder.encode(PASSWORD), AccountRole.STUDENT, false, false));
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    @Test
    void 장애학생시작시각선택지조회_평일만7일이내지금이후시각만정렬되어있음() throws Exception {
        // given
        String token = loginBearer("60231234");
        LocalDateTime before = LocalDateTime.now(clock);

        // when
        String body = mockMvc.perform(get(TIME_OPTIONS).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // then
        List<Map<String, Object>> dates = JsonPath.read(body, "$.data");
        assertThat(dates).isNotEmpty();
        List<LocalDate> days = dates.stream().map(date -> LocalDate.parse((String) date.get("date"))).toList();
        assertThat(days).isSorted().doesNotHaveDuplicates().allSatisfy(day -> {
            assertThat(day).isBetween(before.toLocalDate(), before.toLocalDate().plusDays(HelpRequestSchedule.BOOKABLE_DAYS));
            assertThat(day.getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
            assertThat(helpRequestSchedule.isHoliday(day)).isFalse();
        });
        List<String> startAts = JsonPath.read(body, "$.data[*].startTimes[*].startAt");
        assertThat(startAts).isSorted()
                .allSatisfy(startAt -> assertThat(LocalDateTime.parse(startAt)).isAfter(before));
        List<String> meals = JsonPath.read(body, "$.data[*].startTimes[*].meal");
        assertThat(meals).containsOnly("LUNCH", "DINNER");
    }

    @Test
    void 토큰없이조회_401() throws Exception {
        // when & then
        mockMvc.perform(get(TIME_OPTIONS)).andExpect(status().isUnauthorized());
    }
}
