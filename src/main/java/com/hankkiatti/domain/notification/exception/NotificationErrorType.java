package com.hankkiatti.domain.notification.exception;

import com.hankkiatti.global.response.type.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorType implements ErrorCode {

    // 없는 알림과 남의 알림을 구분하지 않는다
    NOT_FOUND(404, "알림을 찾을 수 없습니다."),
    INVALID_PAGE_SIZE(422, "한 번에 1~50개까지 조회할 수 있습니다.");

    private final int httpStatusCode;
    private final String message;
}
