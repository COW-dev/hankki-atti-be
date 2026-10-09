package com.hankkiatti.domain.student.exception;

import com.hankkiatti.global.exception.DomainException;

public class StudentException extends DomainException {

    public StudentException(StudentErrorType errorType) {
        super(errorType);
    }

    public StudentException(StudentErrorType errorType, String detail) {
        super(errorType, detail);
    }
}
