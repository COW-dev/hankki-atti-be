package com.hankkiatti.domain.auth.exception;

import com.hankkiatti.global.exception.DomainException;

public class AuthException extends DomainException {

    public AuthException(AuthErrorType errorType) {
        super(errorType);
    }

    public AuthException(AuthErrorType errorType, String detail) {
        super(errorType, detail);
    }
}
