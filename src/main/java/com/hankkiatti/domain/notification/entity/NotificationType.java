package com.hankkiatti.domain.notification.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 인앱 알림 종류 (요구사항 6장). 이메일로도 보내는 종류는 BE-52에서 정한다.
 */
@Getter
@RequiredArgsConstructor
public enum NotificationType implements LabeledEnum {

    // 장애학생
    REQUEST_MATCHED("매칭 완료"),
    HELPER_CHANGED("도우미 바뀜"),
    REQUEST_REOPENED("다시 모집 중"),
    REQUEST_FAILED("매칭 실패"),
    // 도우미
    APPLICATION_MATCHED("매칭 완료"),
    WAITING_REGISTERED("예비 등록"),
    PROMOTED("예비에서 승격"),
    PROMOTION_RESPONSE_REQUIRED("승격 응답 요청"),
    STUDENT_CANCELED("학생 취소"),
    WAITING_EXCLUDED("예비 자동 제외"),
    // 공통
    NOTICE_PUBLISHED("새 공지");

    private final String label;
}
