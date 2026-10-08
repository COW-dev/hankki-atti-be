package com.hankkiatti.domain.application.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import java.util.List;
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

    // 진행 중인 지원: 아직 결과가 나지 않은 것. 같은 신청에 다시 지원할 수 없는 기준(재지원 규칙)
    public static final List<ApplicationStatus> ACTIVE = List.of(MATCHED, PROMOTION_PENDING, WAITING);

    // 확정 매칭: 도우미의 시간이 묶인 것. 겹치는 다른 신청에 지원할 수 없는 기준.
    // 승격 응답 대기도 넣는다 — 수락하면 두 건이 겹치기 때문 (요구사항 "논의 필요" 기본값)
    public static final List<ApplicationStatus> CONFIRMED = List.of(MATCHED, PROMOTION_PENDING);

    private final String label;

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    public boolean isConfirmed() {
        return CONFIRMED.contains(this);
    }
}
