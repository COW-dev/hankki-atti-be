package com.hankkiatti.domain.helprequest.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RequestCancelType implements LabeledEnum {

    STUDENT_WITHDRAW("신청 철회"),
    STUDENT_CANCEL("학생 취소"),
    ACCOUNT_DEACTIVATED("계정 비활성화");

    private final String label;
}
