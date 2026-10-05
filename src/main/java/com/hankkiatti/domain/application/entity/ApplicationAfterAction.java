package com.hankkiatti.domain.application.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApplicationAfterAction implements LabeledEnum {

    PROMOTED("예비 승격"),
    REOPENED("모집 재개");

    private final String label;
}
