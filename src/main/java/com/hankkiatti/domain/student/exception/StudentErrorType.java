package com.hankkiatti.domain.student.exception;

import com.hankkiatti.global.response.type.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StudentErrorType implements ErrorCode {

    DUPLICATE_STUDENT_NO(409, "이미 등록된 학번입니다."),
    CREDENTIAL_MAIL_NOT_FAILED(409, "발송 실패 상태의 계정정보 메일만 다시 보낼 수 있습니다."),
    REGISTRATION_CONFLICT(409, "장애학생 등록 요청이 겹쳤습니다. 잠시 후 다시 시도해 주세요."),
    NOT_FOUND(404, "장애학생을 찾을 수 없습니다.");

    private final int httpStatusCode;
    private final String message;
}
