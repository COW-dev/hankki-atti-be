package com.hankkiatti.domain.helper.exception;

import com.hankkiatti.global.response.type.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HelperErrorType implements ErrorCode {

    DUPLICATE_EMAIL(409, "이미 가입된 이메일입니다."),
    DUPLICATE_STUDENT_NO(409, "이미 가입된 학번입니다."),
    // 같은 이메일·학번으로 동시에 가입이 들어와 DB 유니크 제약에 걸린 경우. 다시 보내면 중복 여부를 정확히 알려 준다
    SIGNUP_CONFLICT(409, "가입 요청이 겹쳤습니다. 잠시 후 다시 시도해 주세요.");

    private final int httpStatusCode;
    private final String message;
}
