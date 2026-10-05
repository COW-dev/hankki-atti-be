package com.hankkiatti.domain.account.exception;

import com.hankkiatti.global.exception.DomainException;

public class AccountException extends DomainException {

    public AccountException(AccountErrorType errorType) {
        super(errorType);
    }

    public AccountException(AccountErrorType errorType, String detail) {
        super(errorType, detail);
    }
}
