package com.hankkiatti.domain.notice.exception;

import com.hankkiatti.global.exception.DomainException;

public class NoticeException extends DomainException {

    public NoticeException(NoticeErrorType errorType) {
        super(errorType);
    }

    public NoticeException(NoticeErrorType errorType, String detail) {
        super(errorType, detail);
    }
}
