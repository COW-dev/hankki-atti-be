package com.hankkiatti.domain.sms.service;

/**
 * 문자 발송 실패. 발송 스레드 안에서만 다루고 API 응답으로 나가지 않는다 (Spring의 MailException과 같은 역할).
 */
public class SmsSendException extends RuntimeException {

    public SmsSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
