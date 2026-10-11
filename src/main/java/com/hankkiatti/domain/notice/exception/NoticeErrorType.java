package com.hankkiatti.domain.notice.exception;

import com.hankkiatti.global.response.type.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NoticeErrorType implements ErrorCode {

    NOT_FOUND(404, "공지를 찾을 수 없습니다."),
    // 프론트는 이 코드로 "삭제된 공지예요"를 보여 준다 (이미 받은 알림을 눌렀을 때)
    DELETED(404, "삭제된 공지입니다."),
    INVALID_PAGE(422, "페이지는 0 이상이어야 합니다."),
    DUPLICATE_SUBMIT(409, "방금 같은 제목의 공지를 올렸습니다. 잠시 후 다시 시도해 주세요.");

    private final int httpStatusCode;
    private final String message;
}
