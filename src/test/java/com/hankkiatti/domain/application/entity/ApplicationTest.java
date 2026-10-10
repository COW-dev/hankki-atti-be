package com.hankkiatti.domain.application.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hankkiatti.domain.application.exception.ApplicationErrorType;
import com.hankkiatti.domain.application.exception.ApplicationException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ApplicationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 10, 0);

    private Application waiting() {
        return new Application(null, null, NOW);
    }

    private Application matched() {
        Application application = waiting();
        application.match(NOW);
        return application;
    }

    private Application promotionPending() {
        Application application = waiting();
        application.promote(NOW, NOW.plusMinutes(30));
        return application;
    }

    private void assertError(Runnable action, ApplicationErrorType expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    @Test
    void match_예비_매칭완료() {
        // given
        Application application = waiting();

        // when
        application.match(NOW);

        // then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.MATCHED);
        assertThat(application.getMatchedAt()).isEqualTo(NOW);
    }

    @Test
    void match_매칭완료_예외() {
        // given
        Application application = matched();

        // when & then
        assertError(() -> application.match(NOW), ApplicationErrorType.INVALID_STATUS);
    }

    @Test
    void promote_응답마감있음_승격대기() {
        // given
        Application application = waiting();

        // when
        application.promote(NOW, NOW.plusMinutes(30));

        // then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PROMOTION_PENDING);
        assertThat(application.getPromotedAt()).isEqualTo(NOW);
        assertThat(application.getPromotionDeadline()).isEqualTo(NOW.plusMinutes(30));
        assertThat(application.getMatchedAt()).isNull();
    }

    @Test
    void promote_응답마감없음_바로매칭() {
        // given
        Application application = waiting();

        // when
        application.promote(NOW, null);

        // then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.MATCHED);
        assertThat(application.getPromotedAt()).isEqualTo(NOW);
        assertThat(application.getPromotionDeadline()).isNull();
        assertThat(application.getMatchedAt()).isEqualTo(NOW);
    }

    @Test
    void acceptPromotion_승격대기_매칭완료() {
        // given
        Application application = promotionPending();

        // when
        application.acceptPromotion(NOW.plusMinutes(5));

        // then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.MATCHED);
        assertThat(application.getMatchedAt()).isEqualTo(NOW.plusMinutes(5));
    }

    @Test
    void acceptPromotion_예비_예외() {
        // given
        Application application = waiting();

        // when & then
        assertError(() -> application.acceptPromotion(NOW), ApplicationErrorType.INVALID_STATUS);
    }

    @Test
    void declinePromotion_승격대기_승격거절() {
        // given
        Application application = promotionPending();

        // when
        application.declinePromotion(NOW);

        // then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PROMOTION_DECLINED);
        assertThat(application.getCanceledAt()).isEqualTo(NOW);
    }

    @Test
    void withdraw_예비_빠짐() {
        // given
        Application application = waiting();

        // when
        application.withdraw(NOW);

        // then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
        assertThat(application.getCanceledAt()).isEqualTo(NOW);
    }

    @Test
    void exclude_예비_자동제외() {
        // given
        Application application = waiting();

        // when
        application.exclude();

        // then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.EXCLUDED);
    }

    @Test
    void expire_예비_예비종료() {
        // given
        Application application = waiting();

        // when
        application.expire();

        // then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.EXPIRED);
    }

    @Test
    void cancelByHelper_매칭완료_도우미취소() {
        // given
        Application application = matched();

        // when
        application.cancelByHelper(CancelReason.ILLNESS, null, NOW);

        // then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.HELPER_CANCELED);
        assertThat(application.getCancelReason()).isEqualTo(CancelReason.ILLNESS);
        assertThat(application.getCanceledAt()).isEqualTo(NOW);
    }

    @Test
    void cancelByHelper_기타사유내용없음_예외() {
        // given
        Application application = matched();

        // when & then
        assertError(() -> application.cancelByHelper(CancelReason.OTHER, " ", NOW),
                ApplicationErrorType.CANCEL_REASON_DETAIL_REQUIRED);
    }

    @Test
    void cancelByHelper_관리자비활성화사유_예외() {
        // given
        Application application = matched();

        // when & then
        assertError(() -> application.cancelByHelper(CancelReason.ADMIN_DEACTIVATED, null, NOW),
                ApplicationErrorType.INVALID_CANCEL_REASON);
    }

    @Test
    void cancelByHelper_예비_예외() {
        // given
        Application application = waiting();

        // when & then
        assertError(() -> application.cancelByHelper(CancelReason.ILLNESS, null, NOW),
                ApplicationErrorType.INVALID_STATUS);
    }

    @Test
    void cancelByDeactivation_예비_빠짐() {
        // given
        Application application = waiting();

        // when
        application.cancelByDeactivation(NOW);

        // then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
        assertThat(application.getCancelReason()).isEqualTo(CancelReason.ADMIN_DEACTIVATED);
    }

    @Test
    void cancelByDeactivation_매칭_도우미취소() {
        // given
        Application application = matched();

        // when
        application.cancelByDeactivation(NOW);

        // then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.HELPER_CANCELED);
        assertThat(application.getCancelReason()).isEqualTo(CancelReason.ADMIN_DEACTIVATED);
        assertThat(application.getCanceledAt()).isEqualTo(NOW);
    }

    @Test
    void cancelByStudent_예비_학생취소() {
        // given
        Application application = waiting();

        // when
        application.cancelByStudent();

        // then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.STUDENT_CANCELED);
    }

    @Test
    void recordAfterAction_도우미취소_후속조치기록() {
        // given
        Application application = matched();
        application.cancelByHelper(CancelReason.ACADEMIC, null, NOW);

        // when
        application.recordAfterAction(ApplicationAfterAction.PROMOTED);

        // then
        assertThat(application.getAfterAction()).isEqualTo(ApplicationAfterAction.PROMOTED);
    }

    @Test
    void recordAfterAction_매칭완료_예외() {
        // given
        Application application = matched();

        // when & then
        assertError(() -> application.recordAfterAction(ApplicationAfterAction.REOPENED),
                ApplicationErrorType.INVALID_STATUS);
    }

    @Test
    void complete_봉사시간1시간() {
        // given
        Application application = matched();

        // when
        application.complete();

        // then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.COMPLETED);
        assertThat(application.getVolunteerHours()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void markNoShow_봉사시간0() {
        // given
        Application application = matched();
        application.complete();

        // when
        application.markNoShow();

        // then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.NO_SHOW);
        assertThat(application.getVolunteerHours()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void markNoShow_매칭완료_예외() {
        // given
        Application application = matched();

        // when & then
        assertError(application::markNoShow, ApplicationErrorType.INVALID_STATUS);
    }

    @Test
    void isActive_진행상태_true() {
        // when & then
        assertThat(waiting().isActive()).isTrue();
        assertThat(matched().isActive()).isTrue();
        assertThat(promotionPending().isActive()).isTrue();
    }

    @Test
    void isActive_종료상태_false() {
        // given
        Application application = waiting();
        application.withdraw(NOW);

        // when & then
        assertThat(application.isActive()).isFalse();
    }
}
