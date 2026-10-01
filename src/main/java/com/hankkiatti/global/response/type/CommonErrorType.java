package com.hankkiatti.global.response.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CommonErrorType implements ErrorCode {

    INVALID_REQUEST(400, "요청 형식이 올바르지 않습니다."),
    UNAUTHORIZED(401, "인증이 필요합니다."),
    FORBIDDEN(403, "접근 권한이 없습니다."),
    NOT_FOUND(404, "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(405, "지원하지 않는 HTTP 메서드입니다."),
    NOT_ACCEPTABLE(406, "요청한 미디어 타입을 제공할 수 없습니다."),
    CONFLICT(409, "요청이 현재 상태와 충돌합니다."),
    GONE(410, "요청한 리소스가 만료되었습니다."),
    VALIDATION_FAILED(422, "요청 값 검증에 실패하였습니다."),
    TOO_MANY_REQUESTS(429, "요청 횟수가 초과되었습니다."),
    INTERNAL_ERROR(500, "서버 내부 오류가 발생했습니다.");

    private final int httpStatusCode;
    private final String message;
}
