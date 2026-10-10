package com.hankkiatti.domain.application.integration;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.CancelReason;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.student.entity.DisabilityType;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.support.TestProfiles;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
 * 실제 SecurityFilterChain과 DB로 매칭 현황 조회를 확인한다. 여러 상태의 지원을 직접 넣는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MyApplicationsIntegrationTest {

    private static final String PASSWORD = "hankki!2026";
    private static final String MY_APPLICATIONS = "/api/applications/me";
    private static final String ME = "me@mju.ac.kr";

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

    private LocalDateTime now;
    private Student student;
    private Helper me;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.HOURS);
        // 장애 유형·특이사항·메모가 응답에 새지 않는지 보려고 모두 채운다
        student = studentRepository.save(new Student(saveAccount("60231234", AccountRole.STUDENT), "김한끼",
                "60231234", "010-0000-0000", "kakao_student", "60231234@mju.ac.kr", DisabilityType.PHYSICAL,
                "휠체어 이용"));
        me = saveHelper(ME, "60230001");
    }

    private Account saveAccount(String loginId, AccountRole role) {
        return accountRepository.save(new Account(loginId, passwordEncoder.encode(PASSWORD), role, false, false));
    }

    private Helper saveHelper(String email, String studentNo) {
        return helperRepository.save(TestProfiles.helper(saveAccount(email, AccountRole.HELPER), studentNo));
    }

    private String loginBearer(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private HelpRequest saveRequest(LocalDateTime startAt) {
        return helpRequestRepository.save(new HelpRequest(student, startAt, Set.of(HelpType.SERVING), null, "출입구"));
    }

    private HelpRequest saveMatchedRequest(LocalDateTime startAt, Helper helper) {
        HelpRequest request = saveRequest(startAt);
        request.match(now.minusDays(1));
        Application application = new Application(request, helper, now.minusDays(1));
        application.match(now.minusDays(1));
        applicationRepository.save(application);
        return request;
    }

    private Application saveWaiting(HelpRequest request, Helper helper, LocalDateTime appliedAt) {
        return applicationRepository.save(new Application(request, helper, appliedAt));
    }

    @Test
    void 매칭현황_진행중과지난활동_매칭카드만이름과카톡ID_예비는순번() throws Exception {
        // given
        HelpRequest matched = saveMatchedRequest(now.plusDays(1), me);
        Helper first = saveHelper("first@mju.ac.kr", "60230002");
        Helper ahead = saveHelper("ahead@mju.ac.kr", "60230003");
        HelpRequest crowded = saveMatchedRequest(now.plusDays(2), first);
        saveWaiting(crowded, ahead, now.minusHours(3));
        Application myWaiting = saveWaiting(crowded, me, now.minusHours(2));
        HelpRequest done = saveMatchedRequest(now.minusDays(2), me);
        applicationRepository.findMatchedWithHelper(Set.of(done.getId())).forEach(Application::complete);
        // 다른 도우미의 지원은 나오지 않는다
        saveMatchedRequest(now.plusDays(3), first);
        String token = loginBearer(ME);

        // when & then
        mockMvc.perform(get(MY_APPLICATIONS).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inProgress.length()").value(2))
                .andExpect(jsonPath("$.data.inProgress[0].helpRequestId").value(matched.getId()))
                .andExpect(jsonPath("$.data.inProgress[0].status").value("MATCHED"))
                .andExpect(jsonPath("$.data.inProgress[0].helpTypes[0]").value("SERVING"))
                .andExpect(jsonPath("$.data.inProgress[0].student.name").value("김한끼"))
                .andExpect(jsonPath("$.data.inProgress[0].student.kakaoId").value("kakao_student"))
                .andExpect(jsonPath("$.data.inProgress[0].student.phone").doesNotExist())
                .andExpect(jsonPath("$.data.inProgress[0].student.disabilityType").doesNotExist())
                .andExpect(jsonPath("$.data.inProgress[0].student.specialNote").doesNotExist())
                .andExpect(jsonPath("$.data.inProgress[0].memo").doesNotExist())
                .andExpect(jsonPath("$.data.inProgress[0].waitingOrder").value(nullValue()))
                .andExpect(jsonPath("$.data.inProgress[1].applicationId").value(myWaiting.getId()))
                .andExpect(jsonPath("$.data.inProgress[1].status").value("WAITING"))
                .andExpect(jsonPath("$.data.inProgress[1].waitingOrder").value(2))
                .andExpect(jsonPath("$.data.inProgress[1].student").value(nullValue()))
                .andExpect(jsonPath("$.data.past.length()").value(1))
                .andExpect(jsonPath("$.data.past[0].helpRequestId").value(done.getId()))
                .andExpect(jsonPath("$.data.past[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.past[0].volunteerHours").value(1.0))
                .andExpect(jsonPath("$.data.past[0].student").value(nullValue()));
    }

    @Test
    void 매칭현황_앞예비가자동제외되면_순번당겨짐() throws Exception {
        // given
        Helper first = saveHelper("first@mju.ac.kr", "60230002");
        Helper ahead = saveHelper("ahead@mju.ac.kr", "60230003");
        HelpRequest crowded = saveMatchedRequest(now.plusDays(2), first);
        Application aheadWaiting = saveWaiting(crowded, ahead, now.minusHours(3));
        saveWaiting(crowded, me, now.minusHours(2));
        aheadWaiting.exclude();
        String token = loginBearer(ME);

        // when & then
        mockMvc.perform(get(MY_APPLICATIONS).param("filter", "WAITING").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inProgress.length()").value(1))
                .andExpect(jsonPath("$.data.inProgress[0].waitingOrder").value(1))
                .andExpect(jsonPath("$.data.past.length()").value(0));
    }

    @Test
    void 매칭현황_내가취소한건_지난활동에사유() throws Exception {
        // given
        HelpRequest canceled = saveMatchedRequest(now.plusDays(1), me);
        applicationRepository.findMatchedWithHelper(Set.of(canceled.getId()))
                .forEach(application -> application.cancelByHelper(
                        CancelReason.OTHER, "개인 사정", now));
        String token = loginBearer(ME);

        // when & then
        mockMvc.perform(get(MY_APPLICATIONS).param("filter", "PAST").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inProgress.length()").value(0))
                .andExpect(jsonPath("$.data.past[0].status").value("HELPER_CANCELED"))
                .andExpect(jsonPath("$.data.past[0].cancelReason").value("OTHER"))
                .andExpect(jsonPath("$.data.past[0].cancelReasonDetail").doesNotExist());
    }

    @Test
    void 매칭현황_잘못된필터_400() throws Exception {
        // given
        String token = loginBearer(ME);

        // when & then
        mockMvc.perform(get(MY_APPLICATIONS).param("filter", "SOON").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void 매칭현황_장애학생_403() throws Exception {
        // given
        String token = loginBearer("60231234");

        // when & then
        mockMvc.perform(get(MY_APPLICATIONS).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
    }
}
