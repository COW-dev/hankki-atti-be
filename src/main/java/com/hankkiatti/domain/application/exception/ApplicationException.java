package com.hankkiatti.domain.application.exception;

import com.hankkiatti.global.exception.DomainException;

public class ApplicationException extends DomainException {

    public ApplicationException(ApplicationErrorType errorType) {
        super(errorType);
    }

    public ApplicationException(ApplicationErrorType errorType, String detail) {
        super(errorType, detail);
    }
}
