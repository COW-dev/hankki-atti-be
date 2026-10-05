package com.hankkiatti.domain.account.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AccountRole implements LabeledEnum {

    STUDENT("장애학생"),
    HELPER("도우미"),
    ADMIN("관리자");

    private final String label;
}
