package com.hankkiatti.domain.notification.integration;

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
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.notification.entity.Notification;
import com.hankkiatti.domain.notification.entity.NotificationTargetType;
import com.hankkiatti.domain.notification.entity.NotificationType;
import com.hankkiatti.domain.notification.repository.NotificationRepository;
import com.hankkiatti.domain.notification.service.NotificationService;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.support.TestProfiles;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
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
 * 실제 SecurityFilterChain과 DB로 인앱 알림 API를 확인한다: 내 알림만·최신순·커서, 안 읽은 개수와 읽음.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationIntegrationTest {

    private static final String PASSWORD = "hankki!2026";
    private static final String HELPER_EMAIL = "notified@mju.ac.kr";
    private static final String STUDENT_NO = "60231234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private HelperRepository helperRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long helperId;
    private Long studentId;

    @BeforeEach
    void setUp() {
        Account helperAccount = saveAccount(HELPER_EMAIL, AccountRole.HELPER);
        helperId = helperRepository.save(TestProfiles.helper(helperAccount, "60230001")).getAccountId();
        Account studentAccount = saveAccount(STUDENT_NO, AccountRole.STUDENT);
        studentId = studentRepository.save(TestProfiles.student(studentAccount)).getAccountId();
    }

    private Account saveAccount(String loginId, AccountRole role) {
        return accountRepository.save(new Account(loginId, passwordEncoder.encode(PASSWORD), role, false, false));
    }

    private String loginBearer(String path, String loginId) throws Exception {
        String body = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private String helperToken() throws Exception {
        return loginBearer("/api/auth/login", HELPER_EMAIL);
    }

    private void notifyHelper(int count) {
        for (int i = 1; i <= count; i++) {
            notificationService.notify(helperId, NotificationType.WAITING_REGISTERED, "예비 " + i + "번이에요",
                    NotificationTargetType.APPLICATION, (long) i);
        }
    }

    private List<Notification> helperNotificationsNewestFirst() {
        return notificationRepository.findPage(helperId, null, 100);
    }

    @Test
    void 알림목록_내알림만최신순_커서로이어받기() throws Exception {
        // given — 내 알림 25개, 다른 사람 알림 1개
        notifyHelper(25);
        notificationService.notify(studentId, NotificationType.REQUEST_MATCHED, "매칭됐어요",
                NotificationTargetType.HELP_REQUEST, 1L);
        List<Notification> mine = helperNotificationsNewestFirst();
        String token = helperToken();

        // when & then — 첫 페이지 20개, 가장 최근 것이 맨 위
        String first = mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(20))
                .andExpect(jsonPath("$.data.items[0].id").value(mine.get(0).getId()))
                .andExpect(jsonPath("$.data.items[0].message").value("예비 25번이에요"))
                .andExpect(jsonPath("$.data.items[0].type").value("WAITING_REGISTERED"))
                .andExpect(jsonPath("$.data.items[0].targetType").value("APPLICATION"))
                .andExpect(jsonPath("$.data.items[0].targetId").value(25))
                .andExpect(jsonPath("$.data.items[0].read").value(false))
                .andExpect(jsonPath("$.data.nextCursor").value(mine.get(19).getId()))
                .andReturn().getResponse().getContentAsString();
        Number cursor = JsonPath.read(first, "$.data.nextCursor");

        // then — 다음 페이지 5개로 끝
        mockMvc.perform(get("/api/notifications").param("cursor", cursor.toString())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.data.items.length()").value(5))
                .andExpect(jsonPath("$.data.items[4].message").value("예비 1번이에요"))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @Test
    void 안읽은개수_하나읽음과모두읽음뒤줄어든다() throws Exception {
        // given
        notifyHelper(3);
        Long newest = helperNotificationsNewestFirst().get(0).getId();
        String token = helperToken();
        mockMvc.perform(get("/api/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.data.count").value(3));

        // when — 하나 읽음
        mockMvc.perform(patch("/api/notifications/" + newest + "/read").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(newest))
                .andExpect(jsonPath("$.data.read").value(true));
        mockMvc.perform(get("/api/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.data.count").value(2));

        // when — 모두 읽음 (남은 2개만 바뀐다)
        mockMvc.perform(patch("/api/notifications/read-all").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readCount").value(2));

        // then
        mockMvc.perform(get("/api/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    void 알림읽음_남의알림404_size범위밖422() throws Exception {
        // given
        notificationService.notify(studentId, NotificationType.REQUEST_MATCHED, "매칭됐어요",
                NotificationTargetType.HELP_REQUEST, 1L);
        Long studentsNotification = notificationRepository.findPage(studentId, null, 1).get(0).getId();
        String token = helperToken();

        // when & then
        mockMvc.perform(patch("/api/notifications/" + studentsNotification + "/read")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        mockMvc.perform(get("/api/notifications").param("size", "51").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_INVALID_PAGE_SIZE"));
    }

    @Test
    void 알림_관리자토큰_403() throws Exception {
        // given
        Account adminAccount = saveAccount("center01", AccountRole.ADMIN);
        adminRepository.save(new Admin(adminAccount, "김센터", AdminGrade.FULL));
        String adminToken = loginBearer("/api/admin/auth/login", "center01");

        // when & then
        mockMvc.perform(get("/api/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isForbidden());
    }
}
