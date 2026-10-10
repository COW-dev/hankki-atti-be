package com.hankkiatti.domain.helprequest.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.helprequest.exception.HelpRequestErrorType;
import com.hankkiatti.domain.helprequest.exception.HelpRequestException;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.support.TestAccounts;
import com.hankkiatti.support.TestProfiles;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class HelpRequestTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 5, 12, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 10, 0);

    private HelpRequest newRequest() {
        return new HelpRequest(null, START, Set.of(HelpType.SERVING), null, null);
    }

    private HelpRequest matchedRequest() {
        HelpRequest request = newRequest();
        request.match(NOW);
        return request;
    }

    private HelpRequest completedRequest(LocalDateTime completedAt) {
        HelpRequest request = matchedRequest();
        request.complete(completedAt);
        return request;
    }

    private void assertInvalidStatus(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(HelpRequestException.class)
                .extracting("errorCode")
                .isEqualTo(HelpRequestErrorType.INVALID_STATUS);
    }

    @Test
    void 생성자_종료시각_시작플러스1시간() {
        // when
        HelpRequest request = newRequest();

        // then
        assertThat(request.getEndAt()).isEqualTo(LocalDateTime.of(2026, 10, 5, 13, 0));
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.RECRUITING);
    }

    @Test
    void 생성자_도움유형없음_예외() {
        // when & then
        assertThatThrownBy(() -> new HelpRequest(null, START, Set.of(), null, null))
                .isInstanceOf(HelpRequestException.class)
                .extracting("errorCode")
                .isEqualTo(HelpRequestErrorType.HELP_TYPE_REQUIRED);
    }

    @Test
    void 생성자_기타선택하고내용없음_예외() {
        // when & then
        assertThatThrownBy(() -> new HelpRequest(null, START, Set.of(HelpType.OTHER), " ", null))
                .isInstanceOf(HelpRequestException.class)
                .extracting("errorCode")
                .isEqualTo(HelpRequestErrorType.OTHER_HELP_TEXT_REQUIRED);
    }

    @Test
    void getHelpTypes_외부수정시도_예외() {
        // given
        HelpRequest request = newRequest();

        // when & then
        assertThatThrownBy(() -> request.getHelpTypes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(request.getHelpTypes()).containsExactly(HelpType.SERVING);
    }

    @Test
    void match_모집중_매칭완료되고첫매칭시각기록() {
        // given
        HelpRequest request = newRequest();

        // when
        request.match(NOW);

        // then
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.MATCHED);
        assertThat(request.getFirstMatchedAt()).isEqualTo(NOW);
    }

    @Test
    void match_매칭완료상태_예외() {
        // given
        HelpRequest request = matchedRequest();

        // when & then
        assertInvalidStatus(() -> request.match(NOW));
    }

    @Test
    void match_재매칭_첫매칭시각유지() {
        // given
        HelpRequest request = matchedRequest();
        request.reopen();

        // when
        request.match(NOW.plusMinutes(30));

        // then
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.MATCHED);
        assertThat(request.getFirstMatchedAt()).isEqualTo(NOW);
    }

    @Test
    void changeHelper_매칭완료_도우미변경표시() {
        // given
        HelpRequest request = matchedRequest();

        // when
        request.changeHelper();

        // then
        assertThat(request.isHelperChanged()).isTrue();
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.MATCHED);
    }

    @Test
    void reopen_매칭완료_모집중으로전환() {
        // given
        HelpRequest request = matchedRequest();

        // when
        request.reopen();

        // then
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.RECRUITING);
    }

    @Test
    void fail_모집중_매칭실패() {
        // given
        HelpRequest request = newRequest();

        // when
        request.fail();

        // then
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.FAILED);
    }

    @Test
    void withdraw_모집중_신청철회로취소() {
        // given
        HelpRequest request = newRequest();

        // when
        request.withdraw(NOW);

        // then
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.CANCELED);
        assertThat(request.getCancelType()).isEqualTo(RequestCancelType.STUDENT_WITHDRAW);
        assertThat(request.getCanceledAt()).isEqualTo(NOW);
    }

    @Test
    void withdraw_매칭완료_예외() {
        // given
        HelpRequest request = matchedRequest();

        // when & then
        assertInvalidStatus(() -> request.withdraw(NOW));
    }

    @Test
    void cancelByStudent_매칭완료_학생취소() {
        // given
        HelpRequest request = matchedRequest();

        // when
        request.cancelByStudent(NOW);

        // then
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.CANCELED);
        assertThat(request.getCancelType()).isEqualTo(RequestCancelType.STUDENT_CANCEL);
    }

    @Test
    void cancelByStudent_식사시작시각_예외() {
        // given
        HelpRequest request = matchedRequest();

        // when & then
        assertInvalidStatus(() -> request.cancelByStudent(START));
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.MATCHED);
    }

    @Test
    void cancelByDeactivation_모집중_계정비활성화로취소() {
        // given
        HelpRequest request = newRequest();

        // when
        request.cancelByDeactivation(NOW);

        // then
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.CANCELED);
        assertThat(request.getCancelType()).isEqualTo(RequestCancelType.ACCOUNT_DEACTIVATED);
    }

    @Test
    void cancelByDeactivation_매칭완료_계정비활성화로취소() {
        // given
        HelpRequest request = matchedRequest();

        // when
        request.cancelByDeactivation(NOW);

        // then
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.CANCELED);
        assertThat(request.getCancelType()).isEqualTo(RequestCancelType.ACCOUNT_DEACTIVATED);
    }

    @Test
    void complete_매칭완료_이용완료() {
        // given
        HelpRequest request = matchedRequest();

        // when
        request.complete(START.plusHours(1));

        // then
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.COMPLETED);
        assertThat(request.getCompletedAt()).isEqualTo(START.plusHours(1));
    }

    @Test
    void complete_모집중_예외() {
        // given
        HelpRequest request = newRequest();

        // when & then
        assertInvalidStatus(() -> request.complete(NOW));
    }

    @Test
    void reportNoShow_24시간이내_노쇼전환() {
        // given
        LocalDateTime completedAt = START.plusHours(1);
        HelpRequest request = completedRequest(completedAt);

        // when
        request.reportNoShow(completedAt.plusHours(2));

        // then
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.NO_SHOW);
        assertThat(request.getNoShowReportedAt()).isEqualTo(completedAt.plusHours(2));
    }

    @Test
    void reportNoShow_정확히24시간_허용() {
        // given
        LocalDateTime completedAt = START.plusHours(1);
        HelpRequest request = completedRequest(completedAt);

        // when
        request.reportNoShow(completedAt.plusHours(24));

        // then
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.NO_SHOW);
    }

    @Test
    void reportNoShow_24시간초과_예외() {
        // given
        LocalDateTime completedAt = START.plusHours(1);
        HelpRequest request = completedRequest(completedAt);

        // when & then
        assertThatThrownBy(() -> request.reportNoShow(completedAt.plusHours(24).plusSeconds(1)))
                .isInstanceOf(HelpRequestException.class)
                .extracting("errorCode")
                .isEqualTo(HelpRequestErrorType.NO_SHOW_PERIOD_EXPIRED);
    }

    @Test
    void reportNoShow_매칭완료_예외() {
        // given
        HelpRequest request = matchedRequest();

        // when & then
        assertInvalidStatus(() -> request.reportNoShow(NOW));
    }

    @Test
    void markFeedbackPrompted_상태무관_시각기록() {
        // given
        HelpRequest request = newRequest();

        // when
        request.markFeedbackPrompted(NOW);

        // then
        assertThat(request.getFeedbackPromptedAt()).isEqualTo(NOW);
    }

    @Test
    void overlaps_30분차이_겹침() {
        // given
        HelpRequest request = newRequest();

        // when & then
        assertThat(request.overlaps(START.plusMinutes(30), START.plusMinutes(90))).isTrue();
    }

    @Test
    void overlaps_1시간차이_안겹침() {
        // given
        HelpRequest request = newRequest();

        // when & then
        assertThat(request.overlaps(START.plusHours(1), START.plusHours(2))).isFalse();
        assertThat(request.overlaps(START.minusHours(1), START)).isFalse();
    }

    @Test
    void canReportNoShow_이용완료후24시간정각까지만_가능하고마감시각을줌() {
        // given
        LocalDateTime completedAt = START.plusHours(1);
        HelpRequest request = completedRequest(completedAt);

        // when & then
        assertThat(request.noShowReportDeadline()).isEqualTo(completedAt.plusHours(24));
        assertThat(request.canReportNoShow(completedAt.plusHours(24))).isTrue();
        assertThat(request.canReportNoShow(completedAt.plusHours(24).plusSeconds(1))).isFalse();
    }

    @Test
    void canReportNoShow_이용완료가아니면_불가하고마감시각없음() {
        // given
        HelpRequest matched = matchedRequest();
        HelpRequest noShow = completedRequest(START.plusHours(1));
        noShow.reportNoShow(START.plusHours(2));

        // when & then
        assertThat(matched.canReportNoShow(NOW)).isFalse();
        assertThat(matched.noShowReportDeadline()).isNull();
        assertThat(noShow.canReportNoShow(START.plusHours(3))).isFalse();
    }

    @Test
    void withdraw_식사시작시각부터_예외() {
        // given
        HelpRequest request = newRequest();

        // when & then
        assertInvalidStatus(() -> request.withdraw(START));
        assertInvalidStatus(() -> request.withdraw(START.plusMinutes(1)));
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.RECRUITING);
    }

    @Test
    void isRequestedBy_신청한장애학생만참() {
        // given — 저장 전이라 @MapsId 키를 직접 넣는다
        Student student = TestProfiles.student(TestAccounts.withId(1L, AccountRole.STUDENT, "hash", false));
        ReflectionTestUtils.setField(student, "accountId", 1L);
        HelpRequest request = new HelpRequest(student, START, Set.of(HelpType.SERVING), null, null);

        // when & then
        assertThat(request.isRequestedBy(1L)).isTrue();
        assertThat(request.isRequestedBy(2L)).isFalse();
    }
}
