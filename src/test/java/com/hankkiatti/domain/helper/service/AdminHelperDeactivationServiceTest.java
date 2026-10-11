package com.hankkiatti.domain.helper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.application.service.HelperDeactivationCanceler;
import com.hankkiatti.domain.helper.dto.response.AdminHelperDeactivationPreviewResponseDto;
import com.hankkiatti.domain.helper.dto.response.AdminHelperDeactivationResponseDto;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.exception.HelperErrorType;
import com.hankkiatti.domain.helper.exception.HelperException;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.support.TestAccounts;
import com.hankkiatti.support.TestHelpRequests;
import com.hankkiatti.support.TestProfiles;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminHelperDeactivationServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 11, 10, 0);
    private static final Long HELPER_ID = 7L;
    private static final Long ADMIN_ID = 100L;

    @Mock
    private HelperAccountService helperAccountService;

    @Mock
    private HelperDeactivationCanceler canceler;

    @Mock
    private ApplicationRepository applicationRepository;

    private AdminHelperDeactivationService service;
    private Account account;
    private Helper helper;
    private Student student;
    private long nextId = 10;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        service = new AdminHelperDeactivationService(helperAccountService, canceler, applicationRepository, clock);
        account = TestAccounts.withId(HELPER_ID, AccountRole.HELPER, "hash", false);
        helper = TestProfiles.helper(account, "60230007");
        ReflectionTestUtils.setField(helper, "accountId", HELPER_ID);
        student = TestHelpRequests.student("60231234");
        ReflectionTestUtils.setField(student, "name", "이서준");
    }

    private Application application(Helper of, LocalDateTime startAt) {
        HelpRequest request = TestHelpRequests.request(student, startAt);
        ReflectionTestUtils.setField(request, "id", nextId++);
        request.match(startAt.minusDays(2));
        Application created = new Application(request, of, startAt.minusDays(2));
        ReflectionTestUtils.setField(created, "id", nextId++);
        return created;
    }

    @Test
    void preview_식사전진행중지원만_매칭과예비건수_예비순번() {
        // given
        Application matched = application(helper, NOW.plusDays(1));
        matched.match(NOW.minusDays(1));
        Application waiting = application(helper, NOW.plusDays(2));
        Application aheadOfMe = new Application(waiting.getHelpRequest(), TestHelpRequests.helper("60230008"),
                NOW.minusDays(3));
        ReflectionTestUtils.setField(aheadOfMe, "id", nextId++);
        Application started = application(helper, NOW.minusMinutes(30));
        started.match(NOW.minusDays(1));
        Application finished = application(helper, NOW.minusDays(3));
        finished.match(NOW.minusDays(4));
        finished.complete();
        given(helperAccountService.getAccount(HELPER_ID)).willReturn(account);
        given(applicationRepository.findMineWithHelpRequest(HELPER_ID))
                .willReturn(List.of(waiting, finished, started, matched));
        given(applicationRepository.findWaitingIn(List.of(waiting.getHelpRequest().getId())))
                .willReturn(List.of(aheadOfMe, waiting));

        // when
        AdminHelperDeactivationPreviewResponseDto preview = service.preview(HELPER_ID);

        // then
        assertThat(preview.alreadyInactive()).isFalse();
        assertThat(preview.matchedCount()).isEqualTo(1);
        assertThat(preview.waitingCount()).isEqualTo(1);
        assertThat(preview.targets()).extracting("applicationId").containsExactly(matched.getId(), waiting.getId());
        assertThat(preview.targets().get(0).studentName()).isEqualTo("이서준");
        assertThat(preview.targets().get(0).waitingOrder()).isNull();
        assertThat(preview.targets().get(1).waitingOrder()).isEqualTo(2);
    }

    @Test
    void deactivate_매칭과예비해제_한건실패해도계속하고남은건수를준다() {
        // given
        account.deactivate(NOW);
        given(helperAccountService.deactivate(HELPER_ID, NOW)).willReturn(true);
        given(applicationRepository.findActiveIdsBeforeMeal(HELPER_ID, NOW))
                .willReturn(List.of(1L, 2L, 3L), List.of(3L), List.of(3L));
        given(canceler.release(1L, NOW)).willReturn(Optional.of(ApplicationStatus.MATCHED));
        given(canceler.release(2L, NOW)).willReturn(Optional.of(ApplicationStatus.WAITING));
        given(canceler.release(3L, NOW)).willThrow(new IllegalStateException("lock timeout"));
        given(helperAccountService.getAccount(HELPER_ID)).willReturn(account);

        // when
        AdminHelperDeactivationResponseDto result = service.deactivate(ADMIN_ID, HELPER_ID);

        // then
        assertThat(result.status()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(result.deactivatedAt()).isEqualTo(NOW);
        assertThat(result.releasedMatchCount()).isEqualTo(1);
        assertThat(result.releasedWaitingCount()).isEqualTo(1);
        assertThat(result.remainingCount()).isEqualTo(1);
        verify(canceler, times(2)).release(3L, NOW);
    }

    @Test
    void deactivate_이미비활성인데남은건이있음_남은건만정리한다() {
        // given
        account.deactivate(NOW.minusHours(1));
        given(helperAccountService.deactivate(HELPER_ID, NOW)).willReturn(false);
        given(applicationRepository.findActiveIdsBeforeMeal(HELPER_ID, NOW))
                .willReturn(List.of(3L), List.of(), List.of());
        given(canceler.release(3L, NOW)).willReturn(Optional.of(ApplicationStatus.PROMOTION_PENDING));
        given(helperAccountService.getAccount(HELPER_ID)).willReturn(account);

        // when
        AdminHelperDeactivationResponseDto result = service.deactivate(ADMIN_ID, HELPER_ID);

        // then
        assertThat(result.releasedMatchCount()).isEqualTo(1);
        assertThat(result.remainingCount()).isZero();
    }

    @Test
    void deactivate_이미비활성이고남은건없음_ALREADY_INACTIVE() {
        // given
        given(helperAccountService.deactivate(HELPER_ID, NOW)).willReturn(false);
        given(applicationRepository.findActiveIdsBeforeMeal(HELPER_ID, NOW)).willReturn(List.of());

        // when & then
        assertThatThrownBy(() -> service.deactivate(ADMIN_ID, HELPER_ID))
                .isInstanceOf(HelperException.class)
                .extracting("errorCode").isEqualTo(HelperErrorType.ALREADY_INACTIVE);
    }

    @Test
    void activate_계정상태를돌려준다() {
        // given
        given(helperAccountService.activate(HELPER_ID)).willReturn(account);

        // when & then
        assertThat(service.activate(ADMIN_ID, HELPER_ID).status()).isEqualTo(AccountStatus.ACTIVE);
    }
}
