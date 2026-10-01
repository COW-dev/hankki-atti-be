package com.hankkiatti.global.exception;

import com.hankkiatti.global.response.type.ErrorCode;
import lombok.Getter;

@Getter
public abstract class DomainException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detail;

    protected DomainException(ErrorCode errorCode) {
        this(errorCode, null, null);
    }

    /**
     * detail은 로그에만 남고 API 응답에는 노출되지 않는다.
     * 내부 식별자나 디버그 컨텍스트는 이 생성자를 사용한다.
     */
    protected DomainException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, null);
    }

    /**
     * clientMessage를 지정하면 응답 메시지로 사용된다.
     * errorCode의 기본 메시지보다 구체적인 안내가 사용자에게 필요할 때만 쓴다.
     */
    protected DomainException(ErrorCode errorCode, String detail, String clientMessage) {
        super(clientMessage == null ? errorCode.getMessage() : clientMessage);
        this.errorCode = errorCode;
        this.detail = detail;
    }
}
