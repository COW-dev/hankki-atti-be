package com.hankkiatti.domain.helprequest.exception;

import com.hankkiatti.global.response.type.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HelpRequestErrorType implements ErrorCode {

    // 없는 신청과 다른 장애학생의 신청을 구분하지 않는다 (남의 신청이 있는지 드러내지 않으려고)
    NOT_FOUND(404, "신청을 찾을 수 없습니다."),
    INVALID_STATUS(409, "현재 상태에서는 처리할 수 없는 신청입니다."),
    HELP_TYPE_REQUIRED(422, "필요한 도움을 하나 이상 선택해 주세요."),
    OTHER_HELP_TEXT_REQUIRED(422, "기타 도움 내용을 입력해 주세요."),
    NO_SHOW_PERIOD_EXPIRED(409, "노쇼 신고 기간이 지났습니다."),
    // 지난 시각·주말·공휴일·7일 넘음·선택지에 없는 시각을 구분하지 않는다 (선택지는 time-options가 준다)
    START_TIME_NOT_AVAILABLE(422, "신청할 수 없는 시각입니다. 시작 시각을 다시 골라 주세요."),
    TIME_OVERLAP(409, "이미 신청한 시간과 겹칩니다.");

    private final int httpStatusCode;
    private final String message;
}
