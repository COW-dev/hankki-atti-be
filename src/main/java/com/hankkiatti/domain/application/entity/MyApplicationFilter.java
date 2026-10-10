package com.hankkiatti.domain.application.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 매칭 현황(F-07) 필터 탭. 저장하지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum MyApplicationFilter implements LabeledEnum {

    ALL("전체"),
    MATCHED("매칭"),
    WAITING("예비"),
    PAST("지난 활동");

    private final String label;

    // 예비 탭에는 승격 응답 대기도 넣는다 — 아직 수락하지 않아 확정 매칭이 아니다
    public boolean includes(ApplicationStatus status) {
        return switch (this) {
            case ALL -> true;
            case MATCHED -> status == ApplicationStatus.MATCHED;
            case WAITING -> status == ApplicationStatus.WAITING || status == ApplicationStatus.PROMOTION_PENDING;
            case PAST -> !status.isActive();
        };
    }
}
