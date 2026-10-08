package com.hankkiatti.domain.application.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 도우미가 지금 이 신청에 지원하면 어떻게 되는지 (요청 목록 카드). 저장하지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum ApplyOutcome implements LabeledEnum {

    MATCH("바로 매칭"),
    WAITING("예비 등록"),
    BLOCKED("지원 불가");

    private final String label;
}
