package com.hankkiatti.domain.auth.exception;

import com.hankkiatti.global.response.type.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthErrorType implements ErrorCode {

    LOGIN_FAILED(401, "아이디 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_DEACTIVATED(403, "사용이 중지된 계정이에요. 센터(02-300-1529)로 문의해 주세요."),
    UNAUTHENTICATED(401, "로그인이 필요합니다."),
    INVALID_REFRESH_TOKEN(401, "로그인이 만료되었습니다. 다시 로그인해 주세요."),
    ACCESS_DENIED(403, "접근 권한이 없습니다."),
    PASSWORD_CHANGE_REQUIRED(403, "비밀번호를 먼저 변경해 주세요."),
    CURRENT_PASSWORD_MISMATCH(400, "현재 비밀번호가 올바르지 않습니다."),
    SAME_AS_CURRENT_PASSWORD(422, "현재 비밀번호와 다른 비밀번호를 입력해 주세요."),
    // 만료·사용됨·새 요청으로 무효·없는 토큰을 구분하지 않는다
    PASSWORD_RESET_LINK_INVALID(410, "링크가 만료됐거나 이미 사용됐어요. 비밀번호 재설정을 다시 요청해 주세요.");

    private final int httpStatusCode;
    private final String message;
}
