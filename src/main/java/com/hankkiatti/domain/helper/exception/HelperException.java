package com.hankkiatti.domain.helper.exception;

import com.hankkiatti.global.exception.DomainException;

public class HelperException extends DomainException {

    public HelperException(HelperErrorType errorType) {
        super(errorType);
    }

    public HelperException(HelperErrorType errorType, String detail) {
        super(errorType, detail);
    }
}
