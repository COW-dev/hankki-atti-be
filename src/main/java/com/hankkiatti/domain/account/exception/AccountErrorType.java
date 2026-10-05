package com.hankkiatti.domain.account.exception;

import com.hankkiatti.global.response.type.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AccountErrorType implements ErrorCode {

    NOT_FOUND(404, "계정을 찾을 수 없습니다."),
    ALREADY_INACTIVE(409, "이미 비활성화된 계정입니다."),
    ALREADY_ACTIVE(409, "이미 활성화된 계정입니다.");

    private final int httpStatusCode;
    private final String message;
}
