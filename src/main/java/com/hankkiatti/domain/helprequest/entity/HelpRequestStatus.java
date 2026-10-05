package com.hankkiatti.domain.helprequest.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HelpRequestStatus implements LabeledEnum {

    RECRUITING("모집 중"),
    MATCHED("매칭 완료"),
    FAILED("매칭 실패"),
    CANCELED("취소 완료"),
    COMPLETED("이용 완료"),
    NO_SHOW("노쇼");

    private final String label;
}
