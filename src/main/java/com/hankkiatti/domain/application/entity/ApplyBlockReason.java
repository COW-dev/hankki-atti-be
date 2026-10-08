package com.hankkiatti.domain.application.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 지원할 수 없는 이유. 저장하지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum ApplyBlockReason implements LabeledEnum {

    // 같은 신청에 진행 중인 지원(매칭·승격 응답 대기·예비)이 있다 — 재지원 규칙
    ALREADY_APPLIED("이미 지원한 요청이에요"),
    // 확정 매칭(매칭 완료·승격 응답 대기)과 이용 시간이 겹친다
    TIME_OVERLAP("같은 시간에 매칭된 일정이 있어요");

    private final String label;
}
