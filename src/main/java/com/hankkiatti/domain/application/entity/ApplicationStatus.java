package com.hankkiatti.domain.application.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApplicationStatus implements LabeledEnum {

    MATCHED("매칭 완료"),
    PROMOTION_PENDING("승격 응답 대기"),
    WAITING("예비"),
    WITHDRAWN("빠짐"),
    EXCLUDED("예비 자동 제외"),
    EXPIRED("예비 종료"),
    PROMOTION_DECLINED("승격 거절"),
    HELPER_CANCELED("취소"),
    STUDENT_CANCELED("취소됨 · 학생 사정"),
    COMPLETED("이용 완료"),
    NO_SHOW("노쇼");

    private final String label;
}
