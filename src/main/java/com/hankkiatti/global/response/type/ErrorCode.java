package com.hankkiatti.global.response.type;

import java.util.Locale;

public interface ErrorCode {
    int getHttpStatusCode();
    String getMessage();

    /**
     * 프론트가 오류를 구분하는 코드. "{타입 이름}_{상수 이름}"으로 자동으로 만든다.
     * 예: AuthErrorType.PASSWORD_CHANGE_REQUIRED → AUTH_PASSWORD_CHANGE_REQUIRED, CommonErrorType.NOT_FOUND → COMMON_NOT_FOUND
     * 프론트가 이 값에 의존하므로 ErrorType 클래스나 상수 이름을 바꾸면 API 변경이다.
     */
    default String getCode() {
        if (this instanceof Enum<?> constant) {
            return codePrefix(constant.getDeclaringClass()) + "_" + constant.name();
        }
        return codePrefix(getClass());
    }

    private static String codePrefix(Class<?> type) {
        String name = type.getSimpleName().replaceFirst("ErrorType$", "");
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }
}
