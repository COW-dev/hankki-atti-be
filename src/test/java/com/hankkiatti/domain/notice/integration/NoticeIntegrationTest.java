package com.hankkiatti.domain.notice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.hankkiatti.domain.auth.repository.RefreshTokenRepository;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.notice.dto.request.AdminNoticeSaveRequestDto;
import com.hankkiatti.domain.notice.entity.NoticeTarget;
import com.hankkiatti.domain.notice.exception.NoticeErrorType;
import com.hankkiatti.domain.notice.exception.NoticeException;
import com.hankkiatti.domain.notice.repository.NoticeRepository;
import com.hankkiatti.domain.notice.service.AdminNoticeService;
import com.hankkiatti.domain.notification.entity.Notification;
import com.hankkiatti.domain.notification.entity.NotificationTargetType;
import com.hankkiatti.domain.notification.entity.NotificationType;
import com.hankkiatti.domain.notification.repository.NotificationJobRepository;
import com.hankkiatti.domain.notification.repository.NotificationRepository;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.support.TestProfiles;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 게시 알림은 공지가 커밋된 뒤에 만들어지므로 테스트도 커밋하고 끝나면 지운다.
 * 실제 SecurityFilterChain으로 권한(전체·제한), 대상별 알림 수신자, 역할별 목록·상세, 삭제 안내, 동시 중복 게시를 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NoticeIntegrationTest {

    private static final String PASSWORD = "hankki!2026";
    private static final String FULL_ADMIN = "notice-full";
    private static final String LIMITED_ADMIN = "notice-limited";
    private static final String STUDENT_NO = "60238881";
    private static final String ATTI_HELPER = "notice-atti@mju.ac.kr";
    private static final String HELPER = "notice-helper@mju.ac.kr";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminNoticeService adminNoticeService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private HelperRepository helperRepository;

    @Autowired
    private NoticeRepository noticeRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationJobRepository notificationJobRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Long> accountIds = new ArrayList<>();
    private final List<Long> adminIds = new ArrayList<>();
    private final List<Long> studentIds = new ArrayList<>();
    private final List<Long> helperIds = new ArrayList<>();
    private Long fullAdminId;
    private Long studentId;
    private Long inactiveStudentId;
    private Long attiHelperId;
    private Long helperId;

    @BeforeEach
    void setUp() {
        fullAdminId = saveAdmin(FULL_ADMIN, AdminGrade.FULL);
        saveAdmin(LIMITED_ADMIN, AdminGrade.LIMITED);

        studentId = saveStudent(saveAccount(STUDENT_NO, AccountRole.STUDENT));
        Account inactive = saveAccount("60238882", AccountRole.STUDENT);
        inactive.deactivate(LocalDateTime.now());
        inactiveStudentId = saveStudent(accountRepository.save(inactive));

        attiHelperId = saveHelper(ATTI_HELPER, "60238883", true);
        helperId = saveHelper(HELPER, "60238884", false);
    }

    @AfterEach
    void tearDown() {
        notificationJobRepository.deleteAll();
        notificationRepository.deleteAll();
        noticeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        helperRepository.deleteAllById(helperIds);
        studentRepository.deleteAllById(studentIds);
        adminRepository.deleteAllById(adminIds);
        accountRepository.deleteAllById(accountIds);
    }

    private Account saveAccount(String loginId, AccountRole role) {
        Account account = accountRepository.save(
                new Account(loginId, passwordEncoder.encode(PASSWORD), role, false, false));
        accountIds.add(account.getId());
        return account;
    }

    private Long saveAdmin(String loginId, AdminGrade grade) {
        Long id = adminRepository.save(new Admin(saveAccount(loginId, AccountRole.ADMIN), "홍길동", grade)).getAccountId();
        adminIds.add(id);
        return id;
    }

    private Long saveStudent(Account account) {
        Long id = studentRepository.save(TestProfiles.student(account)).getAccountId();
        studentIds.add(id);
        return id;
    }

    private Long saveHelper(String email, String studentNo, boolean attiMember) {
        Account account = saveAccount(email, AccountRole.HELPER);
        Long id = helperRepository.save(new Helper(account, "이도움", studentNo, email, "010-1111-1111", "kakao",
                attiMember, LocalDateTime.of(2026, 10, 1, 9, 0))).getAccountId();
        helperIds.add(id);
        return id;
    }

    private String login(String path, String loginId) throws Exception {
        String body = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private static String noticeJson(String title, NoticeTarget target, boolean pinned) {
        return "{\"title\":\"" + title + "\",\"body\":\"본문\\n둘째 줄\",\"target\":\"" + target
                + "\",\"pinned\":" + pinned + "}";
    }

    private Long publish(String adminToken, String title, NoticeTarget target, boolean pinned) throws Exception {
        String body = mockMvc.perform(post("/api/admin/notices").header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noticeJson(title, target, pinned)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.noticeId")).longValue();
    }

    // 이 공지로 쌓인 인앱 알림의 받는 사람 (받는 사람은 지연 로딩이라 트랜잭션 안에서 읽는다)
    private List<Long> recipientsOf(Long noticeId) {
        return new TransactionTemplate(transactionManager).execute(status -> notificationRepository.findAll().stream()
                .filter(notification -> notification.getTargetType() == NotificationTargetType.NOTICE
                        && notification.getTargetId().equals(noticeId))
                .peek(notification -> assertThat(notification.getType()).isEqualTo(NotificationType.NOTICE_PUBLISHED))
                .map(Notification::getRecipient)
                .map(Account::getId)
                .toList());
    }

    @Test
    void 게시_대상의활성장애학생도우미에게만알림_수정은재알림없음_제한권한은조회만() throws Exception {
        // given
        String fullToken = login("/api/admin/auth/login", FULL_ADMIN);
        String limitedToken = login("/api/admin/auth/login", LIMITED_ADMIN);

        // when & then — 제한 권한: 작성 403, 목록은 볼 수 있다
        mockMvc.perform(post("/api/admin/notices").header(HttpHeaders.AUTHORIZATION, limitedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noticeJson("제한 권한 공지", NoticeTarget.ALL, false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));

        // when — 전체 권한: 전체·아띠 공지 게시
        Long allNotice = publish(fullToken, "10월 학식당 운영 시간 안내", NoticeTarget.ALL, true);
        Long attiNotice = publish(fullToken, "아띠 정기 회의", NoticeTarget.ATTI, false);

        // then — 비활성 장애학생·관리자는 받지 않는다
        assertThat(recipientsOf(allNotice)).containsExactlyInAnyOrder(studentId, attiHelperId, helperId)
                .doesNotContain(inactiveStudentId, fullAdminId);
        assertThat(recipientsOf(attiNotice)).containsExactly(attiHelperId);
        assertThat(notificationRepository.findAll()).filteredOn(n -> n.getTargetId().equals(allNotice))
                .allSatisfy(n -> assertThat(n.getMessage()).isEqualTo("새 공지가 올라왔어요: 10월 학식당 운영 시간 안내"));

        mockMvc.perform(get("/api/admin/notices").header(HttpHeaders.AUTHORIZATION, limitedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notices[0].noticeId").value(allNotice))
                .andExpect(jsonPath("$.data.notices[0].editedAt").doesNotExist())
                .andExpect(jsonPath("$.data.totalCount").value(2));
        mockMvc.perform(get("/api/admin/notices/" + attiNotice).header(HttpHeaders.AUTHORIZATION, limitedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value("본문\n둘째 줄"))
                .andExpect(jsonPath("$.data.target").value("ATTI"));

        // when — 대상을 넓혀 수정해도 알림을 다시 보내지 않는다
        mockMvc.perform(patch("/api/admin/notices/" + attiNotice).header(HttpHeaders.AUTHORIZATION, fullToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noticeJson("아띠 정기 회의 (장소 변경)", NoticeTarget.ALL, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.target").value("ALL"))
                .andExpect(jsonPath("$.data.editedAt").exists());

        // then
        assertThat(recipientsOf(attiNotice)).containsExactly(attiHelperId);
    }

    @Test
    void 사용자조회_역할과아띠소속별공지_고정먼저_삭제되면안내() throws Exception {
        // given
        String adminToken = login("/api/admin/auth/login", FULL_ADMIN);
        Long allNotice = publish(adminToken, "서비스 이용 안내", NoticeTarget.ALL, false);
        Long studentNotice = publish(adminToken, "신청 방법 안내", NoticeTarget.STUDENT, true);
        Long attiNotice = publish(adminToken, "아띠 정기 회의", NoticeTarget.ATTI, false);
        String studentToken = login("/api/auth/login", STUDENT_NO);
        String attiToken = login("/api/auth/login", ATTI_HELPER);
        String helperToken = login("/api/auth/login", HELPER);

        // when & then — 장애학생: 고정(장애학생) 먼저, 그다음 전체
        mockMvc.perform(get("/api/notices").header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notices.length()").value(2))
                .andExpect(jsonPath("$.data.notices[0].noticeId").value(studentNotice))
                .andExpect(jsonPath("$.data.notices[0].pinned").value(true))
                .andExpect(jsonPath("$.data.notices[1].noticeId").value(allNotice));

        // 아띠 소속 도우미는 아띠 공지까지, 비소속은 전체만
        mockMvc.perform(get("/api/notices").header(HttpHeaders.AUTHORIZATION, attiToken))
                .andExpect(jsonPath("$.data.totalCount").value(2));
        mockMvc.perform(get("/api/notices").header(HttpHeaders.AUTHORIZATION, helperToken))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.notices[0].noticeId").value(allNotice));

        // 상세 — 본문 줄바꿈 그대로, 대상이 아닌 공지는 NOT_FOUND
        mockMvc.perform(get("/api/notices/" + attiNotice).header(HttpHeaders.AUTHORIZATION, attiToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value("본문\n둘째 줄"))
                .andExpect(jsonPath("$.data.target").value("ATTI"));
        mockMvc.perform(get("/api/notices/" + attiNotice).header(HttpHeaders.AUTHORIZATION, helperToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTICE_NOT_FOUND"));

        // when — 삭제
        mockMvc.perform(delete("/api/admin/notices/" + allNotice).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedAt").exists());

        // then — 목록에서 사라지고, 알림으로 들어오면 삭제 안내
        mockMvc.perform(get("/api/notices").header(HttpHeaders.AUTHORIZATION, helperToken))
                .andExpect(jsonPath("$.data.totalCount").value(0));
        mockMvc.perform(get("/api/notices/" + allNotice).header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTICE_DELETED"));
    }

    @Test
    void 같은관리자가같은제목으로동시에두번게시_하나만저장() throws Exception {
        // given
        AdminNoticeSaveRequestDto request = new AdminNoticeSaveRequestDto("10월 학식당 운영 시간 안내", "본문",
                NoticeTarget.ALL, false);
        Callable<Object> create = () -> adminNoticeService.create(fullAdminId, request);

        // when
        List<Object> results = runConcurrently(List.of(create, create));

        // then
        assertThat(results).filteredOn(NoticeException.class::isInstance).singleElement()
                .extracting("errorCode").isEqualTo(NoticeErrorType.DUPLICATE_SUBMIT);
        assertThat(noticeRepository.findAll()).hasSize(1);
    }

    @Test
    void 수정과삭제가동시에_삭제가덮어써지지않음() throws Exception {
        // given
        Long noticeId = adminNoticeService.create(fullAdminId, new AdminNoticeSaveRequestDto("서비스 이용 안내", "본문",
                NoticeTarget.ALL, false)).noticeId();
        AdminNoticeSaveRequestDto edit = new AdminNoticeSaveRequestDto("서비스 이용 안내 (수정)", "새 본문",
                NoticeTarget.HELPER, true);

        // when
        List<Object> results = runConcurrently(List.of(
                () -> adminNoticeService.update(fullAdminId, noticeId, edit),
                () -> adminNoticeService.delete(fullAdminId, noticeId)));

        // then — 어느 쪽이 먼저든 공지는 삭제된 채로 남고, 삭제 뒤에 온 수정은 DELETED로 막힌다
        assertThat(noticeRepository.findById(noticeId).orElseThrow().isDeleted()).isTrue();
        assertThat(results.get(1)).isNotInstanceOf(Exception.class);
        if (results.get(0) instanceof NoticeException rejected) {
            assertThat(rejected.getErrorCode()).isEqualTo(NoticeErrorType.DELETED);
        }
    }

    @Test
    void 작성_검증실패는422_대상값이틀리면400() throws Exception {
        // given
        String adminToken = login("/api/admin/auth/login", FULL_ADMIN);

        // when & then — 제목 101자
        mockMvc.perform(post("/api/admin/notices").header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noticeJson("가".repeat(101), NoticeTarget.ALL, false)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
        mockMvc.perform(post("/api/admin/notices").header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"공지\",\"body\":\"본문\",\"target\":\"FOO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
        assertThat(noticeRepository.findAll()).isEmpty();
    }

    // 동시에 시작해 결과나 예외를 모은다
    private List<Object> runConcurrently(List<Callable<Object>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            ready.await();
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdown();
        }
    }
}
