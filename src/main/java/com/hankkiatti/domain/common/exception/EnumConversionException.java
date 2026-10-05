package com.hankkiatti.domain.common.exception;

import com.hankkiatti.global.exception.DomainException;
import com.hankkiatti.global.response.type.CommonErrorType;

/**
 * DB에 저장된 문자열이 enum 상수와 맞지 않을 때 던진다. 데이터 정합성 문제라 서버 오류로 응답한다.
 */
public class EnumConversionException extends DomainException {

    public EnumConversionException(String detail) {
        super(CommonErrorType.INTERNAL_ERROR, detail);
    }
}
