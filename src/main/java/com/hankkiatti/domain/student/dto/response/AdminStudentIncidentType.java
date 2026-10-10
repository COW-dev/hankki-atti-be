package com.hankkiatti.domain.student.dto.response;

import com.hankkiatti.domain.common.LabeledEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(description = "장애학생 취소·노쇼 이력 유형")
public enum AdminStudentIncidentType implements LabeledEnum {
    REQUEST_WITHDRAWN("신청 철회"),
    STUDENT_CANCELED("학생 취소"),
    ACCOUNT_DEACTIVATED("계정 비활성화"),
    HELPER_CANCELED("도우미 취소"),
    NO_SHOW("노쇼");

    private final String label;
}
