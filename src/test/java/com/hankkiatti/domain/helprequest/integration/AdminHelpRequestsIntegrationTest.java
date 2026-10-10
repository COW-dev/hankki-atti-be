package com.hankkiatti.domain.helprequest.integration;

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
import com.hankkiatti.domain.application.service.ApplicationService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * 실제 SecurityFilterChain과 DB로 관리자 전체 신청 현황을 확인한다: 등급별 응답 모양, 기간·상태·검색, 요약 숫자, 권한.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminHelpRequestsIntegrationTest {

    private static final String PASSWORD = "hankki!2026";
    private static final String LIST = "/api/admin/help-requests";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationService applicationService;

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    private LocalDateTime tomorrowNoon;
    private HelpRequest matched;
    private HelpRequest recruiting;
    private HelpRequest outOfRange;

    @BeforeEach
    void setUp() {
        saveAdmin("center01", AdminGrade.FULL);
        saveAdmin("atti01", AdminGrade.LIMITED);
        Student minji = saveStudent("60231234", "김민지", DisabilityType.VISUAL);
        Student seojun = saveStudent("60235678", "이서준", DisabilityType.PHYSICAL);
        Helper helper = helperRepository.save(
                TestProfiles.helper(saveAccount("helper@mju.ac.kr", AccountRole.HELPER), "60230001"));
        Helper waiting = helperRepository.save(
                TestProfiles.helper(saveAccount("waiting@mju.ac.kr", AccountRole.HELPER), "60230002"));

        tomorrowNoon = LocalDate.now(clock).plusDays(1).atTime(12, 0);
        matched = saveRequest(minji, tomorrowNoon);
        applicationService.apply(helper.getAccountId(), matched.getId());
        applicationService.apply(waiting.getAccountId(), matched.getId());
        recruiting = saveRequest(seojun, tomorrowNoon.plusMinutes(30));
        outOfRange = saveRequest(minji, tomorrowNoon.plusDays(20));
    }

    private Account saveAccount(String loginId, AccountRole role) {
        return accountRepository.save(new Account(loginId, passwordEncoder.encode(PASSWORD), role, false, false));
    }

    private void saveAdmin(String loginId, AdminGrade grade) {
        adminRepository.save(new Admin(saveAccount(loginId, AccountRole.ADMIN), "운영진", grade));
    }

    private Student saveStudent(String studentNo, String name, DisabilityType disabilityType) {
        return studentRepository.save(new Student(saveAccount(studentNo, AccountRole.STUDENT), name, studentNo,
                "010-0000-0000", "kakao" + studentNo, studentNo + "@mju.ac.kr", disabilityType, "특이사항"));
    }

    private HelpRequest saveRequest(Student student, LocalDateTime startAt) {
        return helpRequestRepository.save(new HelpRequest(student, startAt, Set.of(HelpType.SERVING), null, "메모"));
    }

    private String loginBearer(String path, String loginId) throws Exception {
        String body = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private String adminToken(String loginId) throws Exception {
        return loginBearer("/api/admin/auth/login", loginId);
    }

    @Test
    void 전체권한_기본기간식사순_장애유형과도우미예비포함_특이사항메모연락처없음() throws Exception {
        mockMvc.perform(get(LIST).header(HttpHeaders.AUTHORIZATION, adminToken("center01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(matched.getId()))
                .andExpect(jsonPath("$.data[0].status").value("MATCHED"))
                .andExpect(jsonPath("$.data[0].student.name").value("김민지"))
                .andExpect(jsonPath("$.data[0].student.studentNo").value("60231234"))
                .andExpect(jsonPath("$.data[0].student.disabilityType").value("VISUAL"))
                .andExpect(jsonPath("$.data[0].student.specialNote").doesNotExist())
                .andExpect(jsonPath("$.data[0].student.phone").doesNotExist())
                .andExpect(jsonPath("$.data[0].memo").doesNotExist())
                .andExpect(jsonPath("$.data[0].helper.name").value("이도움"))
                .andExpect(jsonPath("$.data[0].waitingCount").value(1))
                .andExpect(jsonPath("$.data[0].minutesToMatch").value(0))
                .andExpect(jsonPath("$.data[1].id").value(recruiting.getId()))
                .andExpect(jsonPath("$.data[1].helper").doesNotExist())
                .andExpect(jsonPath("$.data[1].firstMatchedAt").doesNotExist());
    }

    @Test
    void 제한권한_장애유형필드자체가없음() throws Exception {
        mockMvc.perform(get(LIST).header(HttpHeaders.AUTHORIZATION, adminToken("atti01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].student.name").value("김민지"))
                .andExpect(jsonPath("$.data[0].student.disabilityType").doesNotExist())
                .andExpect(jsonPath("$.data[0].helper.name").value("이도움"));
    }

    @Test
    void 상태필터와검색어_기간밖은제외() throws Exception {
        String token = adminToken("center01");
        mockMvc.perform(get(LIST).param("status", "RECRUITING").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(recruiting.getId()));
        // 학번 일부로 검색, 기간을 넓히면 20일 뒤 신청도 나온다
        mockMvc.perform(get(LIST).param("q", "1234")
                        .param("from", tomorrowNoon.toLocalDate().toString())
                        .param("to", tomorrowNoon.toLocalDate().plusDays(25).toString())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].id").value(outOfRange.getId()));
        mockMvc.perform(get(LIST).param("q", "이서").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].student.name").value("이서준"));
    }

    @Test
    void 요약숫자_선택기간기준() throws Exception {
        mockMvc.perform(get(LIST + "/summary").header(HttpHeaders.AUTHORIZATION, adminToken("atti01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.matched").value(1))
                .andExpect(jsonPath("$.data.recruiting").value(1))
                .andExpect(jsonPath("$.data.failed").value(0))
                .andExpect(jsonPath("$.data.noShow").value(0));
    }

    @Test
    void 잘못된기간422_잘못된상태400_장애학생토큰403() throws Exception {
        String token = adminToken("center01");
        mockMvc.perform(get(LIST).param("from", "2026-10-20").param("to", "2026-10-10")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("HELP_REQUEST_INVALID_DATE_RANGE"));
        mockMvc.perform(get(LIST).param("status", "SOON").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(LIST).header(HttpHeaders.AUTHORIZATION, loginBearer("/api/auth/login", "60231234")))
                .andExpect(status().isForbidden());
    }
}
