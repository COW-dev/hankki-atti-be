package com.hankkiatti.domain.student.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DisabilityType implements LabeledEnum {

    KIDNEY_HEART_STOMA("간/신장/심장/장루요루"),
    SPEECH("언어"),
    AUTISM_BRAIN_LESION("자폐/뇌병변"),
    INTELLECTUAL("지적"),
    PHYSICAL("지체"),
    HEARING("청각"),
    VISUAL("시각");

    private final String label;
}
