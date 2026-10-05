package com.hankkiatti.domain.helprequest.exception;

import com.hankkiatti.global.response.type.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HelpRequestErrorType implements ErrorCode {

    INVALID_STATUS(409, "현재 상태에서는 처리할 수 없는 신청입니다."),
    HELP_TYPE_REQUIRED(422, "필요한 도움을 하나 이상 선택해 주세요."),
    OTHER_HELP_TEXT_REQUIRED(422, "기타 도움 내용을 입력해 주세요."),
    NO_SHOW_PERIOD_EXPIRED(409, "노쇼 신고 기간이 지났습니다.");

    private final int httpStatusCode;
    private final String message;
}
