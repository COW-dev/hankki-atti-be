package com.hankkiatti.domain.sms.service;

/**
 * 문자 발송부. 발신 서비스를 바꿀 때(예: AWS SNS → 센터 번호로 보내는 국내 서비스) 이 구현체만 교체한다.
 */
public interface SmsSender {

    boolean isConfigured();

    /**
     * @param recipient E.164 형식 수신 번호 (+821012345678)
     * @throws SmsSendException 발송 실패
     */
    void send(String recipient, String body);
}
