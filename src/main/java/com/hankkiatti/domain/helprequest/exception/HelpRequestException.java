package com.hankkiatti.domain.helprequest.exception;

import com.hankkiatti.global.exception.DomainException;

public class HelpRequestException extends DomainException {

    public HelpRequestException(HelpRequestErrorType errorType) {
        super(errorType);
    }

    public HelpRequestException(HelpRequestErrorType errorType, String detail) {
        super(errorType, detail);
    }
}
