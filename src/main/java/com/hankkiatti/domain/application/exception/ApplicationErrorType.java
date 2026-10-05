package com.hankkiatti.domain.application.exception;

import com.hankkiatti.global.response.type.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApplicationErrorType implements ErrorCode {

    INVALID_STATUS(409, "현재 상태에서는 처리할 수 없는 지원입니다."),
    CANCEL_REASON_DETAIL_REQUIRED(422, "기타 사유를 입력해 주세요."),
    INVALID_CANCEL_REASON(422, "선택할 수 없는 취소 사유입니다.");

    private final int httpStatusCode;
    private final String message;
}
