package com.hankkiatti.domain.helprequest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.helprequest.dto.request.HelpRequestCreateRequestDto;
import com.hankkiatti.domain.helprequest.dto.response.HelpRequestCreateResponseDto;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.exception.HelpRequestErrorType;
import com.hankkiatti.domain.helprequest.exception.HelpRequestException;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.support.TestAccounts;
import com.hankkiatti.support.TestProfiles;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class HelpRequestServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 2026-10-12(월) 09:00
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
    private static final LocalDateTime NOON = MONDAY.atTime(12, 0);
    private static final Long STUDENT_ID = 1L;

    @Mock
    private HelpRequestRepository helpRequestRepository;

    @Mock
    private StudentRepository studentRepository;

    private HelpRequestService helpRequestService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(MONDAY.atTime(9, 0).atZone(SEOUL).toInstant(), SEOUL);
        helpRequestService = new HelpRequestService(helpRequestRepository, studentRepository, new HelpRequestSchedule(),
                clock);
    }

    private void givenStudentWithoutOverlap(LocalDateTime startAt) {
        Student student = TestProfiles.student(TestAccounts.withId(STUDENT_ID, AccountRole.STUDENT, "hash", false));
        given(studentRepository.findByIdForUpdate(STUDENT_ID)).willReturn(Optional.of(student));
        given(helpRequestRepository.existsOverlapping(STUDENT_ID, startAt, startAt.plusHours(1))).willReturn(false);
        given(helpRequestRepository.save(any(HelpRequest.class))).willAnswer(invocation -> {
            HelpRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });
    }

    private HelpRequestCreateRequestDto request(LocalDateTime startAt, Set<HelpType> helpTypes, String otherHelpText,
                                                String memo) {
        return new HelpRequestCreateRequestDto(startAt, helpTypes, otherHelpText, memo);
    }

    private void assertErrorType(Runnable call, HelpRequestErrorType expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(HelpRequestException.class)
                .extracting("errorCode").isEqualTo(expected);
    }

    @Test
    void create_정상_모집중으로저장하고요약반환() {
        // given
        givenStudentWithoutOverlap(NOON);

        // when
        HelpRequestCreateResponseDto result = helpRequestService.create(STUDENT_ID,
                request(NOON, Set.of(HelpType.OTHER, HelpType.SERVING), " 식판 반납 ", " 출입구에서 기다릴게요 "));

        // then
        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.startAt()).isEqualTo(NOON);
        assertThat(result.endAt()).isEqualTo(NOON.plusHours(1));
        assertThat(result.helpTypes()).containsExactly(HelpType.SERVING, HelpType.OTHER);
        assertThat(result.otherHelpText()).isEqualTo("식판 반납");
        assertThat(result.memo()).isEqualTo("출입구에서 기다릴게요");
        assertThat(result.status()).isEqualTo(HelpRequestStatus.RECRUITING);
    }

    @Test
    void create_기타를안고르면기타내용버리고_빈메모는없음으로저장() {
        // given
        givenStudentWithoutOverlap(NOON);

        // when
        helpRequestService.create(STUDENT_ID, request(NOON, Set.of(HelpType.SEATING), "무시될 내용", "   "));

        // then
        ArgumentCaptor<HelpRequest> saved = ArgumentCaptor.forClass(HelpRequest.class);
        verify(helpRequestRepository).save(saved.capture());
        assertThat(saved.getValue().getOtherHelpText()).isNull();
        assertThat(saved.getValue().getMemo()).isNull();
    }

    @Test
    void create_선택지에없는시각_START_TIME_NOT_AVAILABLE이고DB조회없음() {
        // when & then — 토요일, 30분 단위 아님, 이미 지난 시각
        assertErrorType(() -> helpRequestService.create(STUDENT_ID,
                request(MONDAY.plusDays(5).atTime(12, 0), Set.of(HelpType.SERVING), null, null)),
                HelpRequestErrorType.START_TIME_NOT_AVAILABLE);
        assertErrorType(() -> helpRequestService.create(STUDENT_ID,
                request(MONDAY.atTime(12, 15), Set.of(HelpType.SERVING), null, null)),
                HelpRequestErrorType.START_TIME_NOT_AVAILABLE);
        assertErrorType(() -> helpRequestService.create(STUDENT_ID,
                request(MONDAY.minusDays(1).atTime(12, 0), Set.of(HelpType.SERVING), null, null)),
                HelpRequestErrorType.START_TIME_NOT_AVAILABLE);
        verify(studentRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void create_내신청과시간이겹침_TIME_OVERLAP이고저장안함() {
        // given
        Student student = TestProfiles.student(TestAccounts.withId(STUDENT_ID, AccountRole.STUDENT, "hash", false));
        given(studentRepository.findByIdForUpdate(STUDENT_ID)).willReturn(Optional.of(student));
        given(helpRequestRepository.existsOverlapping(STUDENT_ID, NOON, NOON.plusHours(1))).willReturn(true);

        // when & then
        assertErrorType(() -> helpRequestService.create(STUDENT_ID, request(NOON, Set.of(HelpType.SERVING), null, null)),
                HelpRequestErrorType.TIME_OVERLAP);
        verify(helpRequestRepository, never()).save(any());
    }

    @Test
    void create_장애학생이아닌계정_ACCESS_DENIED() {
        // given
        given(studentRepository.findByIdForUpdate(2L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> helpRequestService.create(2L, request(NOON, Set.of(HelpType.SERVING), null, null)))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.ACCESS_DENIED);
        verify(helpRequestRepository, never()).save(any());
    }
}
