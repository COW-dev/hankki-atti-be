package com.hankkiatti.domain.application.exception;

import com.hankkiatti.global.response.type.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApplicationErrorType implements ErrorCode {

    INVALID_STATUS(409, "현재 상태에서는 처리할 수 없는 지원입니다."),
    CANCEL_REASON_DETAIL_REQUIRED(422, "기타 사유를 입력해 주세요."),
    INVALID_CANCEL_REASON(422, "선택할 수 없는 취소 사유입니다."),
    // 모집 중·매칭 완료가 아니거나 식사가 이미 시작된 신청
    NOT_OPEN(409, "지원할 수 없는 요청입니다."),
    // 같은 신청에 진행 중 지원(매칭·승격 응답 대기·예비)이 있다
    ALREADY_APPLIED(409, "이미 지원한 요청입니다."),
    // 확정 매칭(매칭 완료·승격 응답 대기)과 이용 시간이 겹친다
    TIME_OVERLAP(409, "같은 시간에 매칭된 일정이 있습니다.");

    private final int httpStatusCode;
    private final String message;
}
