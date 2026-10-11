package com.hankkiatti.domain.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.admin.entity.AdminGrade;
import com.hankkiatti.domain.admin.repository.AdminRepository;
import com.hankkiatti.domain.application.dto.request.VolunteerHoursUpdateRequestDto;
import com.hankkiatti.domain.application.dto.response.AdminVolunteerHoursResponseDto;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.entity.VolunteerHourChange;
import com.hankkiatti.domain.application.exception.ApplicationErrorType;
import com.hankkiatti.domain.application.exception.ApplicationException;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.application.repository.VolunteerHourChangeRepository;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.support.TestAccounts;
import com.hankkiatti.support.TestHelpRequests;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminVolunteerHourServiceTest {

    private static final LocalDateTime MEAL = LocalDateTime.of(2026, 10, 8, 12, 0);
    private static final Long ADMIN_ID = 100L;
    private static final Long APPLICATION_ID = 20L;
    private static final Long REQUEST_ID = 10L;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private HelpRequestRepository helpRequestRepository;

    @Mock
    private VolunteerHourChangeRepository volunteerHourChangeRepository;

    private AdminVolunteerHourService service;
    private HelpRequest request;
    private Application application;

    @BeforeEach
    void setUp() {
        service = new AdminVolunteerHourService(adminRepository, applicationRepository, helpRequestRepository,
                volunteerHourChangeRepository);
        request = TestHelpRequests.request(TestHelpRequests.student("60231234"), MEAL);
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        request.match(MEAL.minusDays(1));
        application = new Application(request, TestHelpRequests.helper("60230007"), MEAL.minusDays(1));
        ReflectionTestUtils.setField(application, "id", APPLICATION_ID);
        application.match(MEAL.minusDays(1));
    }

    private void givenAdmin(AdminGrade grade) {
        Admin admin = new Admin(TestAccounts.withId(ADMIN_ID, AccountRole.ADMIN, "hash", false), "김센터", grade);
        given(adminRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin));
    }

    private void givenLocked() {
        given(applicationRepository.findHelpRequestIdById(APPLICATION_ID)).willReturn(Optional.of(REQUEST_ID));
        given(helpRequestRepository.findByIdForUpdate(REQUEST_ID)).willReturn(Optional.of(request));
        given(applicationRepository.findByIdForUpdate(APPLICATION_ID)).willReturn(Optional.of(application));
    }

    private static VolunteerHoursUpdateRequestDto request(String hours) {
        return new VolunteerHoursUpdateRequestDto(new BigDecimal(hours), "  식사 보조가 길어졌어요 ");
    }

    @Test
    void change_이용완료_시간을바꾸고이력을남긴다() {
        // given
        application.complete();
        givenAdmin(AdminGrade.FULL);
        givenLocked();
        given(volunteerHourChangeRepository.save(any(VolunteerHourChange.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        AdminVolunteerHoursResponseDto result = service.change(ADMIN_ID, APPLICATION_ID, request("1.50"));

        // then
        assertThat(application.getVolunteerHours()).isEqualByComparingTo("1.5");
        assertThat(application.getVolunteerHours().scale()).isEqualTo(1);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.COMPLETED);
        ArgumentCaptor<VolunteerHourChange> saved = ArgumentCaptor.forClass(VolunteerHourChange.class);
        verify(volunteerHourChangeRepository).save(saved.capture());
        assertThat(saved.getValue().getBeforeHours()).isEqualByComparingTo("1.0");
        assertThat(saved.getValue().getAfterHours()).isEqualByComparingTo("1.5");
        assertThat(saved.getValue().getReason()).isEqualTo("식사 보조가 길어졌어요");
        assertThat(result.volunteerHours()).isEqualByComparingTo("1.5");
        assertThat(result.change().adminName()).isEqualTo("김센터");
    }

    @Test
    void change_노쇼_상태는그대로두고시간만바꾼다() {
        // given
        application.complete();
        application.markNoShow();
        givenAdmin(AdminGrade.FULL);
        givenLocked();
        given(volunteerHourChangeRepository.save(any(VolunteerHourChange.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        service.change(ADMIN_ID, APPLICATION_ID, request("1.0"));

        // then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.NO_SHOW);
        assertThat(application.getVolunteerHours()).isEqualByComparingTo("1.0");
    }

    @Test
    void change_지금값과같음_VOLUNTEER_HOURS_UNCHANGED이고이력없음() {
        // given
        application.complete();
        givenAdmin(AdminGrade.FULL);
        givenLocked();

        // when & then
        assertThatThrownBy(() -> service.change(ADMIN_ID, APPLICATION_ID, request("1")))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCode").isEqualTo(ApplicationErrorType.VOLUNTEER_HOURS_UNCHANGED);
        verify(volunteerHourChangeRepository, never()).save(any());
    }

    @Test
    void change_매칭완료건_INVALID_STATUS() {
        // given
        givenAdmin(AdminGrade.FULL);
        givenLocked();

        // when & then
        assertThatThrownBy(() -> service.change(ADMIN_ID, APPLICATION_ID, request("1.5")))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCode").isEqualTo(ApplicationErrorType.INVALID_STATUS);
        verify(volunteerHourChangeRepository, never()).save(any());
    }

    @Test
    void change_0점5단위아님_INVALID_VOLUNTEER_HOURS이고지원을읽지않음() {
        // given
        givenAdmin(AdminGrade.FULL);

        // when & then
        assertThatThrownBy(() -> service.change(ADMIN_ID, APPLICATION_ID, request("1.3")))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCode").isEqualTo(ApplicationErrorType.INVALID_VOLUNTEER_HOURS);
        verify(applicationRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void change_제한권한_ACCESS_DENIED이고지원을읽지않음() {
        // given
        givenAdmin(AdminGrade.LIMITED);

        // when & then
        assertThatThrownBy(() -> service.change(ADMIN_ID, APPLICATION_ID, request("1.5")))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.ACCESS_DENIED);
        verify(applicationRepository, never()).findHelpRequestIdById(any());
    }

    @Test
    void change_없는지원_NOT_FOUND() {
        // given
        givenAdmin(AdminGrade.FULL);
        given(applicationRepository.findHelpRequestIdById(APPLICATION_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.change(ADMIN_ID, APPLICATION_ID, request("1.5")))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCode").isEqualTo(ApplicationErrorType.NOT_FOUND);
    }
}
