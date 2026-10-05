package com.hankkiatti.domain.account.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AccountStatus implements LabeledEnum {

    ACTIVE("활성"),
    INACTIVE("비활성");

    private final String label;
}
