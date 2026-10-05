package com.hankkiatti.domain.application.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CancelReason implements LabeledEnum {

    ILLNESS("질병·부상"),
    ACADEMIC("학사 일정 변경"),
    FAMILY("가족 경조사"),
    OTHER("기타"),
    // 관리자가 도우미 계정을 비활성화할 때 시스템이 기록한다. 도우미가 직접 고를 수 없다
    ADMIN_DEACTIVATED("관리자 비활성화");

    private final String label;
}
