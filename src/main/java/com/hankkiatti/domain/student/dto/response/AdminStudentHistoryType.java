package com.hankkiatti.domain.student.dto.response;

import com.hankkiatti.domain.common.LabeledEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 취소·노쇼 이력 구분. 저장하지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum AdminStudentHistoryType implements LabeledEnum {

    HELPER_CANCELED("도우미 취소"),
    STUDENT_CANCELED("학생 취소"),
    NO_SHOW("노쇼 신고");

    private final String label;
}
