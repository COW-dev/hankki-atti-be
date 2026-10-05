package com.hankkiatti.domain.admin.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AdminGrade implements LabeledEnum {

    FULL("전체 권한"),
    LIMITED("제한 권한");

    private final String label;
}
