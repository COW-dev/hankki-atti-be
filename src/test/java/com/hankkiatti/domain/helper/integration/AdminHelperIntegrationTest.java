package com.hankkiatti.domain.helper.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.admin.entity.AdminGrade;
import com.hankkiatti.domain.admin.repository.AdminRepository;
import com.hankkiatti.domain.application.dto.request.HelperCancelRequestDto;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.CancelReason;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.application.service.ApplicationService;
import com.hankkiatti.domain.application.service.HelperCancelService;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.helprequest.service.HelpRequestService;
import com.hankkiatti.domain.student.entity.DisabilityType;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.support.TestProfiles;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * 실제 SecurityFilterChain과 DB로 관리자 도우미 목록·상세·활동 이력을 확인한다. 활동 데이터는 실제 지원·취소·승격 흐름으로 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminHelperIntegrationTest {

    private static final String PASSWORD = "hankki!2026";
    private static final String STUDENT_NO = "60231234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private HelperCancelService helperCancelService;

    @Autowired
    private HelpRequestService helpRequestService;

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

    @Autowired
    private Clock clock;

    private Student student;
    private final List<Long> helperIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        adminRepository.save(new Admin(saveAccount("center01", AccountRole.ADMIN), "김센터", AdminGrade.FULL));
        adminRepository.save(new Admin(saveAccount("atti01", AccountRole.ADMIN), "이운영", AdminGrade.LIMITED));
        student = studentRepository.save(new Student(saveAccount(STUDENT_NO, AccountRole.STUDENT), "김민지", STUDENT_NO,
                "010-1234-1234", "minji_k", STUDENT_NO + "@mju.ac.kr", DisabilityType.VISUAL, "메뉴 읽어 주기"));
        for (int i = 0; i < 2; i++) {
            Helper helper = helperRepository.save(
                    TestProfiles.helper(saveAccount("admin-helper" + i + "@mju.ac.kr", AccountRole.HELPER),
                            "6029000" + i));
            helperIds.add(helper.getAccountId());
        }
    }

    private Account saveAccount(String loginId, AccountRole role) {
        return accountRepository.save(new Account(loginId, passwordEncoder.encode(PASSWORD), role, false, false));
    }

    private String loginBearer(String loginPath, String loginId) throws Exception {
        String body = mockMvc.perform(post(loginPath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private String adminToken(String loginId) throws Exception {
        return loginBearer("/api/admin/auth/login", loginId);
    }

    private HelpRequest saveRequest(LocalDateTime startAt) {
        return helpRequestRepository.save(new HelpRequest(student, startAt, Set.of(HelpType.SERVING), null, "메모"));
    }

    private Long apply(int helperIndex, HelpRequest request) {
        return applicationService.apply(helperIds.get(helperIndex), request.getId()).applicationId();
    }

    /**
     * 도우미0의 활동: 내일 학사 일정으로 취소 → 예비 1번 도우미1 승격 / 모레 예비로만 있다 빠짐 /
     * 글피 매칭 뒤 장애학생 취소 / 지난주 이용 완료.
     */
    private void givenActivities() {
        LocalDateTime tomorrowNoon = LocalDate.now(clock).plusDays(1).atTime(12, 0);
        HelpRequest promotedRequest = saveRequest(tomorrowNoon);
        Long canceled = apply(0, promotedRequest);
        apply(1, promotedRequest);
        helperCancelService.cancel(helperIds.get(0), canceled, new HelperCancelRequestDto(CancelReason.ACADEMIC, null));

        HelpRequest waitedOnly = saveRequest(tomorrowNoon.plusDays(1));
        apply(1, waitedOnly);
        Long waiting = apply(0, waitedOnly);
        helperCancelService.leave(helperIds.get(0), waiting);

        HelpRequest studentCanceled = saveRequest(tomorrowNoon.plusDays(2));
        apply(0, studentCanceled);
        helpRequestService.cancelMatched(student.getAccountId(), studentCanceled.getId());

        LocalDateTime lastWeek = tomorrowNoon.minusDays(8);
        HelpRequest completed = saveRequest(lastWeek);
        completed.match(lastWeek.minusDays(1));
        Application completedApplication = new Application(completed,
                helperRepository.findById(helperIds.get(0)).orElseThrow(), lastWeek.minusDays(1));
        completedApplication.match(lastWeek.minusDays(1));
        completedApplication.complete();
        applicationRepository.save(completedApplication);
        completed.complete(lastWeek.plusHours(1));
    }

    @Test
    void 목록_제한권한도조회_학번검색_전화번호가림_이용완료건수() throws Exception {
        // given
        givenActivities();

        // when & then
        mockMvc.perform(get("/api/admin/helpers").param("q", "60290000")
                        .header(HttpHeaders.AUTHORIZATION, adminToken("atti01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.helpers[0].helperId").value(helperIds.get(0)))
                .andExpect(jsonPath("$.data.helpers[0].maskedPhone").value("010-****-1111"))
                .andExpect(jsonPath("$.data.helpers[0].phone").doesNotExist())
                .andExpect(jsonPath("$.data.helpers[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.helpers[0].completedCount").value(1));
    }

    @Test
    void 상세_전체전화번호와활동요약() throws Exception {
        // given
        givenActivities();

        // when & then — 매칭 3(내일·글피·지난주), 이용 완료 1, 봉사시간 1.0, 취소 1
        mockMvc.perform(get("/api/admin/helpers/" + helperIds.get(0))
                        .header(HttpHeaders.AUTHORIZATION, adminToken("center01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("010-1111-1111"))
                .andExpect(jsonPath("$.data.summary.matchedCount").value(3))
                .andExpect(jsonPath("$.data.summary.completedCount").value(1))
                .andExpect(jsonPath("$.data.summary.volunteerHours").value(1.0))
                .andExpect(jsonPath("$.data.summary.canceledCount").value(1))
                .andExpect(jsonPath("$.data.summary.noShowCount").value(0));
    }

    @Test
    void 활동이력_예비로만있던건빠지고_최근식사부터_장애학생은이름만() throws Exception {
        // given
        givenActivities();

        // when & then — 글피 학생 취소, 내일 도우미 취소(예비 1번 승격), 지난주 이용 완료
        mockMvc.perform(get("/api/admin/helpers/" + helperIds.get(0) + "/activities")
                        .header(HttpHeaders.AUTHORIZATION, adminToken("atti01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].status").value("STUDENT_CANCELED"))
                .andExpect(jsonPath("$.data[0].studentName").value("김민지"))
                .andExpect(jsonPath("$.data[0].disabilityType").doesNotExist())
                .andExpect(jsonPath("$.data[0].memo").doesNotExist())
                .andExpect(jsonPath("$.data[1].status").value("HELPER_CANCELED"))
                .andExpect(jsonPath("$.data[1].cancelReason").value("ACADEMIC"))
                .andExpect(jsonPath("$.data[1].afterAction").value("PROMOTED"))
                .andExpect(jsonPath("$.data[1].promotedWaitingOrder").value(1))
                .andExpect(jsonPath("$.data[2].status").value("COMPLETED"));
    }

    @Test
    void 없는도우미404_장애학생도우미토큰403() throws Exception {
        mockMvc.perform(get("/api/admin/helpers/999999").header(HttpHeaders.AUTHORIZATION, adminToken("atti01")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HELPER_NOT_FOUND"));
        mockMvc.perform(get("/api/admin/helpers")
                        .header(HttpHeaders.AUTHORIZATION, loginBearer("/api/auth/login", STUDENT_NO)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/helpers")
                        .header(HttpHeaders.AUTHORIZATION, loginBearer("/api/auth/login", "admin-helper0@mju.ac.kr")))
                .andExpect(status().isForbidden());
    }

    @Test
    void 목록_음수페이지_422() throws Exception {
        mockMvc.perform(get("/api/admin/helpers").param("page", "-1")
                        .header(HttpHeaders.AUTHORIZATION, adminToken("center01")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("HELPER_INVALID_PAGE"));
    }
}
