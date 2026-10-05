package com.hankkiatti.domain.helprequest.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HelpType implements LabeledEnum {

    SERVING("배식 보조"),
    SEATING("좌석 안내"),
    MOVING("이동·운반 보조"),
    OTHER("기타");

    private final String label;
}
