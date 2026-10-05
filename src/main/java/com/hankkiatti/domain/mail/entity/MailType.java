package com.hankkiatti.domain.mail.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 메일 종류. 계정 메일과 기능명세서의 이메일 알림 4종.
 */
@Getter
@RequiredArgsConstructor
public enum MailType implements LabeledEnum {

    STUDENT_CREDENTIAL("장애학생 계정정보"),
    TEMPORARY_PASSWORD("임시 비밀번호"),
    PASSWORD_RESET("비밀번호 재설정"),
    MATCHED("매칭 완료"),
    PROMOTED("예비에서 승격"),
    COUNTERPART_CANCELED("상대방 취소"),
    MATCH_FAILED("매칭 실패");

    private final String label;
}
