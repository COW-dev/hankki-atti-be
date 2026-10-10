package com.hankkiatti.domain.application.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.application.service.ApplicationService;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.helprequest.scheduler.MealTimeJob;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.support.TestProfiles;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
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
 * 실제 SecurityFilterChain과 DB로 식사 1시간 이내 승격의 응답 흐름을 확인한다.
 * 도우미 취소 → 응답 대기 → 거절·수락, 응답 마감 자동 거절, 식사 시작까지 응답이 없을 때의 매칭 실패.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PromotionResponseIntegrationTest {

    private static final String PASSWORD = "hankki!2026";
    private static final DateTimeFormatter JSON_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String CANCEL_BODY = "{\"reason\":\"ILLNESS\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private MealTimeJob mealTimeJob;

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

    private LocalDateTime now;
    private Student student;
    // 0: 매칭됐다 취소하는 도우미, 1·2: 예비
    private final List<Helper> helpers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        student = studentRepository.save(TestProfiles.student(saveAccount("60231234", AccountRole.STUDENT)));
        for (int i = 0; i < 3; i++) {
            helpers.add(helperRepository.save(
                    TestProfiles.helper(saveAccount(email(i), AccountRole.HELPER), "6023000" + i)));
        }
    }

    private static String email(int index) {
        return "promotion" + index + "@mju.ac.kr";
    }

    private Account saveAccount(String loginId, AccountRole role) {
        return accountRepository.save(new Account(loginId, passwordEncoder.encode(PASSWORD), role, false, false));
    }

    private String loginBearer(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    // 도우미0 매칭, 도우미1·2 예비인 신청. 지원 ID를 도우미 순으로 돌려준다
    private List<Long> matchedWithWaiting(HelpRequest request, int waitingCount) {
        List<Long> applicationIds = new ArrayList<>();
        for (int i = 0; i <= waitingCount; i++) {
            applicationIds.add(applicationService.apply(helpers.get(i).getAccountId(), request.getId()).applicationId());
        }
        return applicationIds;
    }

    private HelpRequest saveRequest(LocalDateTime startAt) {
        return helpRequestRepository.save(new HelpRequest(student, startAt, Set.of(HelpType.SERVING), null, null));
    }

    private void cancelBy(int helperIndex, Long applicationId) throws Exception {
        mockMvc.perform(post("/api/applications/" + applicationId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, loginBearer(email(helperIndex)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CANCEL_BODY))
                .andExpect(status().isOk());
    }

    private ApplicationStatus statusOf(Long applicationId) {
        return applicationRepository.findById(applicationId).orElseThrow().getStatus();
    }

    @Test
    void 승격응답_취소뒤응답대기_거절하면다음예비_수락하면매칭() throws Exception {
        // given — 40분 뒤 식사. 도우미0이 취소하면 도우미1이 응답 대기(마감 = 식사 15분 전)
        LocalDateTime startAt = now.plusMinutes(40);
        HelpRequest request = saveRequest(startAt);
        List<Long> ids = matchedWithWaiting(request, 2);
        cancelBy(0, ids.get(0));
        String first = loginBearer(email(1));
        String second = loginBearer(email(2));

        // then — 매칭 현황에 응답 마감이 보인다
        mockMvc.perform(get("/api/applications/me").header(HttpHeaders.AUTHORIZATION, first))
                .andExpect(jsonPath("$.data.inProgress[0].status").value("PROMOTION_PENDING"))
                .andExpect(jsonPath("$.data.inProgress[0].promotionDeadline")
                        .value(startAt.minusMinutes(15).format(JSON_DATE_TIME)))
                .andExpect(jsonPath("$.data.inProgress[0].student").doesNotExist());

        // when — 도우미1 거절 → 도우미2 응답 대기
        mockMvc.perform(post("/api/applications/" + ids.get(1) + "/promotion/decline")
                        .header(HttpHeaders.AUTHORIZATION, first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROMOTION_DECLINED"))
                .andExpect(jsonPath("$.data.student").doesNotExist());
        assertThat(statusOf(ids.get(2))).isEqualTo(ApplicationStatus.PROMOTION_PENDING);

        // when — 도우미2 수락
        mockMvc.perform(post("/api/applications/" + ids.get(2) + "/promotion/accept")
                        .header(HttpHeaders.AUTHORIZATION, second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MATCHED"))
                .andExpect(jsonPath("$.data.student.name").value("김한끼"))
                .andExpect(jsonPath("$.data.student.kakaoId").value("kakao_student"))
                .andExpect(jsonPath("$.data.promotionDeadline").doesNotExist());

        // then
        HelpRequest reloaded = helpRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(HelpRequestStatus.MATCHED);
        assertThat(reloaded.isHelperChanged()).isTrue();
    }

    @Test
    void 승격응답_마지막예비가거절_모집재개() throws Exception {
        // given
        HelpRequest request = saveRequest(now.plusMinutes(40));
        List<Long> ids = matchedWithWaiting(request, 1);
        cancelBy(0, ids.get(0));

        // when
        mockMvc.perform(post("/api/applications/" + ids.get(1) + "/promotion/decline")
                        .header(HttpHeaders.AUTHORIZATION, loginBearer(email(1))))
                .andExpect(status().isOk());

        // then
        assertThat(helpRequestRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(HelpRequestStatus.RECRUITING);
    }

    @Test
    void 승격응답_마감이지나면_자동거절하고다음예비가응답대기() throws Exception {
        // given
        LocalDateTime startAt = now.plusMinutes(40);
        HelpRequest request = saveRequest(startAt);
        List<Long> ids = matchedWithWaiting(request, 2);
        cancelBy(0, ids.get(0));

        // when — 마감 1분 전에는 그대로, 마감에 자동 거절
        mealTimeJob.processDue(startAt.minusMinutes(16));
        assertThat(statusOf(ids.get(1))).isEqualTo(ApplicationStatus.PROMOTION_PENDING);
        mealTimeJob.processDue(startAt.minusMinutes(15));

        // then — 다음 예비는 15분 전 이후 승격이라 식사 시작까지 응답 대기
        assertThat(statusOf(ids.get(1))).isEqualTo(ApplicationStatus.PROMOTION_DECLINED);
        assertThat(statusOf(ids.get(2))).isEqualTo(ApplicationStatus.PROMOTION_PENDING);
        assertThat(applicationRepository.findById(ids.get(2)).orElseThrow().getPromotionDeadline()).isEqualTo(startAt);
    }

    @Test
    void 승격응답_식사15분전이후승격되고응답없음_식사시작에매칭실패() throws Exception {
        // given — 10분 뒤 식사. 마감이 식사 시작이라 그 전에는 자동 거절하지 않는다
        LocalDateTime startAt = now.plusMinutes(10);
        HelpRequest request = saveRequest(startAt);
        List<Long> ids = matchedWithWaiting(request, 2);
        cancelBy(0, ids.get(0));
        mealTimeJob.processDue(startAt.minusMinutes(1));
        assertThat(statusOf(ids.get(1))).isEqualTo(ApplicationStatus.PROMOTION_PENDING);

        // when
        mealTimeJob.processDue(startAt);

        // then — 응답 대기는 승격 거절, 남은 예비는 예비 종료, 신청은 매칭 실패
        assertThat(statusOf(ids.get(1))).isEqualTo(ApplicationStatus.PROMOTION_DECLINED);
        assertThat(statusOf(ids.get(2))).isEqualTo(ApplicationStatus.EXPIRED);
        assertThat(helpRequestRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(HelpRequestStatus.FAILED);
    }

    @Test
    void 승격응답_남의지원_404_응답대기아님_409() throws Exception {
        // given
        HelpRequest request = saveRequest(now.plusMinutes(40));
        List<Long> ids = matchedWithWaiting(request, 2);
        cancelBy(0, ids.get(0));

        // when & then — 도우미2가 도우미1의 승격을 수락
        mockMvc.perform(post("/api/applications/" + ids.get(1) + "/promotion/accept")
                        .header(HttpHeaders.AUTHORIZATION, loginBearer(email(2))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
        // 도우미2는 아직 예비
        mockMvc.perform(post("/api/applications/" + ids.get(2) + "/promotion/accept")
                        .header(HttpHeaders.AUTHORIZATION, loginBearer(email(2))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_INVALID_STATUS"));
    }
}
