package com.hankkiatti.domain.notification.exception;

import com.hankkiatti.global.exception.DomainException;

public class NotificationException extends DomainException {

    public NotificationException(NotificationErrorType errorType) {
        super(errorType);
    }

    public NotificationException(NotificationErrorType errorType, String detail) {
        super(errorType, detail);
    }
}
