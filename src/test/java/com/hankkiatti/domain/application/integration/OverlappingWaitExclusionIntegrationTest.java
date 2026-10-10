package com.hankkiatti.domain.application.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.application.dto.request.HelperCancelRequestDto;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.entity.CancelReason;
import com.hankkiatti.domain.application.exception.ApplicationException;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.application.service.ApplicationService;
import com.hankkiatti.domain.application.service.HelperCancelService;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.mail.repository.MailOutboxRepository;
import com.hankkiatti.domain.notification.repository.NotificationRepository;
import com.hankkiatti.domain.sms.repository.SmsOutboxRepository;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.support.TestProfiles;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 확정 매칭이 커밋되면 겹치는 다른 예비가 자동 제외되는지 실제 커밋·이벤트로 확인한다 (요구사항 4.3). 끝나면 지운다.
 */
@SpringBootTest
class OverlappingWaitExclusionIntegrationTest {

    private static final int ROUNDS = 6;
    private static final HelperCancelRequestDto ILLNESS = new HelperCancelRequestDto(CancelReason.ILLNESS, null);

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private HelperCancelService helperCancelService;

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
    private NotificationRepository notificationRepository;

    @Autowired
    private MailOutboxRepository mailOutboxRepository;

    @Autowired
    private SmsOutboxRepository smsOutboxRepository;

    @Autowired
    private Clock clock;

    private final List<Long> accountIds = new ArrayList<>();
    // 0: 지켜볼 도우미, 1~3: 다른 도우미
    private final List<Long> helperIds = new ArrayList<>();
    private Student student;
    private LocalDateTime tomorrowNoon;

    @BeforeEach
    void setUp() {
        Account studentAccount = accountRepository.save(new Account("60239995", "hash", AccountRole.STUDENT, false, false));
        accountIds.add(studentAccount.getId());
        student = studentRepository.save(TestProfiles.student(studentAccount));
        for (int i = 0; i < 4; i++) {
            Account account = accountRepository.save(
                    new Account("exclude-helper" + i + "@mju.ac.kr", "hash", AccountRole.HELPER, false, false));
            accountIds.add(account.getId());
            helperIds.add(helperRepository.save(TestProfiles.helper(account, "6027000" + i)).getAccountId());
        }
        tomorrowNoon = LocalDateTime.now(clock).plusDays(1).truncatedTo(ChronoUnit.DAYS).withHour(12);
    }

    @AfterEach
    void tearDown() {
        // 커밋된 지원·취소가 알림과 메일·문자 아웃박스를 쌓으므로 함께 지운다
        notificationRepository.deleteAll();
        mailOutboxRepository.deleteAll();
        smsOutboxRepository.deleteAll();
        applicationRepository.deleteAll();
        helpRequestRepository.deleteAll();
        helperIds.forEach(helperRepository::deleteById);
        studentRepository.deleteById(student.getAccountId());
        accountIds.forEach(accountRepository::deleteById);
    }

    private Long saveRequest(LocalDateTime startAt) {
        return helpRequestRepository.save(new HelpRequest(student, startAt, Set.of(HelpType.SERVING), null, null))
                .getId();
    }

    // 다른 도우미가 매칭돼 있는 신청을 만든다 (그 뒤 지원하면 예비가 된다)
    private Long saveMatchedBy(int helperIndex, LocalDateTime startAt) {
        Long requestId = saveRequest(startAt);
        applicationService.apply(helperIds.get(helperIndex), requestId);
        return requestId;
    }

    private ApplicationStatus statusOf(Long applicationId) {
        return applicationRepository.findById(applicationId).orElseThrow().getStatus();
    }

    private List<Long> waitingHelperIds(Long helpRequestId) {
        return applicationRepository.findByHelpRequestIdAndStatus(helpRequestId, ApplicationStatus.WAITING).stream()
                .map(application -> application.getHelper().getAccountId()).toList();
    }

    @Test
    void apply_바로매칭되면_겹치는다른예비는제외되고_안겹치는예비와다른사람예비는유지() {
        // given — 도우미0이 12:00·12:30·14:00 신청에 예비, 12:00 신청에는 도우미2가 도우미0 뒤 예비
        Long noonMatched = saveMatchedBy(1, tomorrowNoon);
        Long twoPmMatched = saveMatchedBy(3, tomorrowNoon.plusHours(2));
        Long waitNoon = applicationService.apply(helperIds.get(0), noonMatched).applicationId();
        applicationService.apply(helperIds.get(2), noonMatched);
        Long waitTwoPm = applicationService.apply(helperIds.get(0), twoPmMatched).applicationId();
        // 12:30 신청은 도우미3이 매칭 (도우미2가 매칭되면 도우미2의 12:00 예비가 먼저 제외돼 버린다)
        Long halfPast = saveMatchedBy(3, tomorrowNoon.plusMinutes(30));
        Long waitHalfPast = applicationService.apply(helperIds.get(0), halfPast).applicationId();
        Long open = saveRequest(tomorrowNoon);

        // when — 도우미0이 12:00 모집 중 신청에 바로 매칭 (응답이 나온 시점)
        applicationService.apply(helperIds.get(0), open);

        // then
        assertThat(statusOf(waitNoon)).isEqualTo(ApplicationStatus.EXCLUDED);
        assertThat(statusOf(waitHalfPast)).isEqualTo(ApplicationStatus.EXCLUDED);
        assertThat(statusOf(waitTwoPm)).isEqualTo(ApplicationStatus.WAITING);
        // 12:00 신청의 예비는 도우미2만 남아 1번으로 당겨진다
        assertThat(waitingHelperIds(noonMatched)).containsExactly(helperIds.get(2));
    }

    @Test
    void cancel_예비에서승격돼매칭되면_겹치는다른예비가제외된다() {
        // given — 도우미0이 12:00 신청에 예비 1번, 12:30 신청에도 예비
        Long noon = saveMatchedBy(1, tomorrowNoon);
        applicationService.apply(helperIds.get(0), noon);
        Long halfPast = saveMatchedBy(2, tomorrowNoon.plusMinutes(30));
        Long waitHalfPast = applicationService.apply(helperIds.get(0), halfPast).applicationId();
        Long matchedByOne = applicationRepository.findByHelpRequestIdAndStatus(noon, ApplicationStatus.MATCHED)
                .get(0).getId();

        // when — 도우미1 취소 → 도우미0 승격
        helperCancelService.cancel(helperIds.get(1), matchedByOne, ILLNESS);

        // then
        assertThat(applicationRepository.findByHelpRequestIdAndStatus(noon, ApplicationStatus.MATCHED)).singleElement()
                .extracting(application -> application.getHelper().getAccountId()).isEqualTo(helperIds.get(0));
        assertThat(statusOf(waitHalfPast)).isEqualTo(ApplicationStatus.EXCLUDED);
    }

    @Test
    void 매칭과겹치는신청의승격이동시에_교착없이끝나고확정매칭은하나_겹치는예비도남지않는다() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            // given — 12:00 신청: 도우미1 매칭·도우미0 예비 / 12:30 신청: 모집 중 / 12:00 다른 신청: 도우미2 매칭·도우미0 예비
            LocalDateTime noon = tomorrowNoon.plusDays(round);
            Long promoting = saveMatchedBy(1, noon);
            applicationService.apply(helperIds.get(0), promoting);
            Long open = saveRequest(noon.plusMinutes(30));
            Long other = saveMatchedBy(2, noon);
            Long waitOther = applicationService.apply(helperIds.get(0), other).applicationId();
            Long matchedByOne = applicationRepository.findByHelpRequestIdAndStatus(promoting, ApplicationStatus.MATCHED)
                    .get(0).getId();

            // when — 도우미1 취소(도우미0 승격)와 도우미0의 12:30 지원이 동시에
            List<Object> results = runConcurrently(List.of(
                    () -> helperCancelService.cancel(helperIds.get(1), matchedByOne, ILLNESS),
                    () -> applicationService.apply(helperIds.get(0), open)));

            // then — 교착 없이 끝나고(지원이 겹침으로 막히는 건 정상), 도우미0은 겹치는 확정 매칭이 하나, 겹치는 예비도 없다
            assertThat(results).allSatisfy(result -> assertThat(result)
                    .satisfiesAnyOf(ok -> assertThat(ok).isNotInstanceOf(Exception.class),
                            error -> assertThat(error).isInstanceOf(ApplicationException.class)));
            // 이번 라운드의 두 신청(12:00 승격 대상·12:30)만 센다 — 앞 라운드의 매칭은 다른 날짜다
            long confirmed = applicationRepository.findActiveWithHelpRequestByHelperId(helperIds.get(0)).stream()
                    .filter(application -> application.getStatus() == ApplicationStatus.MATCHED)
                    .filter(application -> List.of(promoting, open).contains(application.getHelpRequest().getId()))
                    .count();
            assertThat(confirmed).isEqualTo(1);
            assertThat(statusOf(waitOther)).isEqualTo(ApplicationStatus.EXCLUDED);
        }
    }

    private List<Object> runConcurrently(List<Callable<?>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> task : tasks) {
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
                // 교착이면 여기서 시간 초과로 실패한다
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
