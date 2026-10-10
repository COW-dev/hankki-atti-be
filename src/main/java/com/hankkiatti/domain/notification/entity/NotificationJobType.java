package com.hankkiatti.domain.notification.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 알림 작업 종류 = 알림을 만들 일. 대상(target_id)과 값(detail)의 뜻은 종류마다 다르다.
 */
@Getter
@RequiredArgsConstructor
public enum NotificationJobType implements LabeledEnum {

    // 대상: 지원 ID, 값: 확정 종류(HelperConfirmedEvent.Kind)
    HELPER_CONFIRMED("확정 매칭"),
    // 대상: 지원 ID, 값: 재알림이면 true
    PROMOTION_PENDING("승격 응답 요청"),
    // 대상: 지원 ID, 값: 예비 순번
    WAITING_REGISTERED("예비 등록"),
    // 대상: 지원 ID
    WAITING_EXCLUDED("예비 자동 제외"),
    // 대상: 신청 ID
    REQUEST_REOPENED("다시 모집 중"),
    // 대상: 신청 ID
    REQUEST_FAILED("매칭 실패"),
    // 대상: 학생 사정 취소가 된 지원 ID (지원마다 작업 하나)
    STUDENT_CANCELED("학생 취소");

    private final String label;
}
