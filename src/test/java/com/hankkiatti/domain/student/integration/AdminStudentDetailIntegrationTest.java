package com.hankkiatti.domain.student.integration;

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
 * 실제 SecurityFilterChain과 DB로 관리자 장애학생 상세 3탭을 확인한다. 이력 데이터는 실제 지원·취소·승격 흐름으로 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminStudentDetailIntegrationTest {

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
    private String path;

    @BeforeEach
    void setUp() {
        adminRepository.save(new Admin(saveAccount("center01", AccountRole.ADMIN), "김센터", AdminGrade.FULL));
        adminRepository.save(new Admin(saveAccount("atti01", AccountRole.ADMIN), "이운영", AdminGrade.LIMITED));
        student = studentRepository.save(new Student(saveAccount(STUDENT_NO, AccountRole.STUDENT), "김민지", STUDENT_NO,
                "010-1234-1234", "minji_k", STUDENT_NO + "@mju.ac.kr", DisabilityType.VISUAL, "메뉴 읽어 주기"));
        for (int i = 0; i < 3; i++) {
            Helper helper = helperRepository.save(
                    TestProfiles.helper(saveAccount("detail" + i + "@mju.ac.kr", AccountRole.HELPER), "6023000" + i));
            helperIds.add(helper.getAccountId());
        }
        path = "/api/admin/students/" + student.getAccountId();
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
     * 내일·모레·글피 신청 3건과 지난 노쇼 1건.
     * 내일: 도우미0 학사 일정으로 취소 → 예비 1번 도우미1 승격 / 모레: 도우미2 기타 사유 취소 → 모집 재개 / 글피: 장애학생 매칭 취소
     */
    private void givenHistory() {
        LocalDateTime tomorrowNoon = LocalDate.now(clock).plusDays(1).atTime(12, 0);
        HelpRequest promotedRequest = saveRequest(tomorrowNoon);
        Long canceled = apply(0, promotedRequest);
        apply(1, promotedRequest);
        helperCancelService.cancel(helperIds.get(0), canceled, new HelperCancelRequestDto(CancelReason.ACADEMIC, null));

        HelpRequest reopened = saveRequest(tomorrowNoon.plusDays(1));
        Long otherCanceled = apply(2, reopened);
        helperCancelService.cancel(helperIds.get(2), otherCanceled,
                new HelperCancelRequestDto(CancelReason.OTHER, "알바 시간이 겹쳤어요"));

        HelpRequest studentCanceled = saveRequest(tomorrowNoon.plusDays(2));
        apply(0, studentCanceled);
        helpRequestService.cancelMatched(student.getAccountId(), studentCanceled.getId());

        // 지난주 노쇼 — 이용 완료 3시간 뒤 신고
        LocalDateTime lastWeek = tomorrowNoon.minusDays(8);
        HelpRequest noShow = saveRequest(lastWeek);
        noShow.match(lastWeek.minusDays(1));
        Application noShowApplication = new Application(noShow,
                helperRepository.findById(helperIds.get(1)).orElseThrow(), lastWeek.minusDays(1));
        noShowApplication.match(lastWeek.minusDays(1));
        noShowApplication.complete();
        noShowApplication.markNoShow();
        applicationRepository.save(noShowApplication);
        noShow.complete(lastWeek.plusHours(1));
        noShow.reportNoShow(lastWeek.plusHours(4));
    }

    @Test
    void 정보탭_전체권한은연락처장애유형특이사항_제한권한은403() throws Exception {
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, adminToken("center01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("김민지"))
                .andExpect(jsonPath("$.data.disabilityType").value("VISUAL"))
                .andExpect(jsonPath("$.data.phone").value("010-1234-1234"))
                .andExpect(jsonPath("$.data.loginId").value(STUDENT_NO))
                .andExpect(jsonPath("$.data.specialNote").value("메뉴 읽어 주기"));
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, adminToken("atti01")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
    }

    @Test
    void 매칭현황탭_최근식사부터_승격순번_제한권한프로필에장애유형없음() throws Exception {
        // given
        givenHistory();

        // when & then
        mockMvc.perform(get(path + "/help-requests").header(HttpHeaders.AUTHORIZATION, adminToken("atti01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.student.name").value("김민지"))
                .andExpect(jsonPath("$.data.student.disabilityType").doesNotExist())
                .andExpect(jsonPath("$.data.helpRequests.length()").value(4))
                .andExpect(jsonPath("$.data.helpRequests[0].status").value("CANCELED"))
                .andExpect(jsonPath("$.data.helpRequests[1].status").value("RECRUITING"))
                .andExpect(jsonPath("$.data.helpRequests[2].status").value("MATCHED"))
                .andExpect(jsonPath("$.data.helpRequests[2].helper.name").value("이도움"))
                .andExpect(jsonPath("$.data.helpRequests[2].promoted").value(true))
                .andExpect(jsonPath("$.data.helpRequests[2].promotedWaitingOrder").value(1))
                .andExpect(jsonPath("$.data.helpRequests[2].helperChanged").value(true))
                .andExpect(jsonPath("$.data.helpRequests[2].memo").doesNotExist())
                .andExpect(jsonPath("$.data.helpRequests[3].status").value("NO_SHOW"));
    }

    @Test
    void 취소노쇼이력탭_도우미취소승격_모집재개_학생취소_노쇼() throws Exception {
        // given
        givenHistory();

        // when & then — 최근 식사부터: 글피 학생 취소, 모레 도우미 취소(모집 재개), 내일 도우미 취소(승격), 지난주 노쇼
        mockMvc.perform(get(path + "/histories").header(HttpHeaders.AUTHORIZATION, adminToken("center01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.student.disabilityType").value("VISUAL"))
                .andExpect(jsonPath("$.data.histories.length()").value(4))
                .andExpect(jsonPath("$.data.histories[0].type").value("STUDENT_CANCELED"))
                .andExpect(jsonPath("$.data.histories[0].helper").doesNotExist())
                .andExpect(jsonPath("$.data.histories[1].type").value("HELPER_CANCELED"))
                .andExpect(jsonPath("$.data.histories[1].cancelReason").value("OTHER"))
                .andExpect(jsonPath("$.data.histories[1].cancelReasonDetail").value("알바 시간이 겹쳤어요"))
                .andExpect(jsonPath("$.data.histories[1].afterAction").value("REOPENED"))
                .andExpect(jsonPath("$.data.histories[1].requestStatus").value("RECRUITING"))
                .andExpect(jsonPath("$.data.histories[2].type").value("HELPER_CANCELED"))
                .andExpect(jsonPath("$.data.histories[2].cancelReason").value("ACADEMIC"))
                .andExpect(jsonPath("$.data.histories[2].afterAction").value("PROMOTED"))
                .andExpect(jsonPath("$.data.histories[2].promotedHelper.id").value(helperIds.get(1)))
                .andExpect(jsonPath("$.data.histories[2].promotedWaitingOrder").value(1))
                .andExpect(jsonPath("$.data.histories[3].type").value("NO_SHOW"))
                .andExpect(jsonPath("$.data.histories[3].minutesAfterCompletion").value(180));
    }

    @Test
    void 없는학생404_장애학생토큰403() throws Exception {
        mockMvc.perform(get("/api/admin/students/999999/histories")
                        .header(HttpHeaders.AUTHORIZATION, adminToken("atti01")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
        mockMvc.perform(get(path + "/help-requests")
                        .header(HttpHeaders.AUTHORIZATION, loginBearer("/api/auth/login", STUDENT_NO)))
                .andExpect(status().isForbidden());
    }
}
