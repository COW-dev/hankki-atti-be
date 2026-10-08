package com.hankkiatti.domain.sms.service;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

/**
 * AWS SNS로 문자 한 통을 보낸다.
 * sms.sns.enabled가 false면 SnsClient 빈이 없다 → 보내지 않고 문자를 대기 상태로 둔다 (설정 전 문자가 실패로 버려지지 않게).
 * 한국 수신 번호는 발신자 ID·발신번호를 지정할 수 없어 아무것도 지정하지 않는다 (해외 번호에서 [국제발신]으로 간다).
 */
@Component
@RequiredArgsConstructor
public class SnsSmsSender implements SmsSender {

    // 알림은 모두 시간이 급한 거래성 문자라 전달 우선순위가 높은 Transactional로 보낸다
    static final String SMS_TYPE_ATTRIBUTE = "AWS.SNS.SMS.SMSType";
    static final String TRANSACTIONAL = "Transactional";

    private final ObjectProvider<SnsClient> snsClient;

    @Override
    public boolean isConfigured() {
        return snsClient.getIfAvailable() != null;
    }

    @Override
    public void send(String recipient, String body) {
        PublishRequest request = PublishRequest.builder()
                .phoneNumber(recipient)
                .message(body)
                .messageAttributes(Map.of(SMS_TYPE_ATTRIBUTE, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(TRANSACTIONAL)
                        .build()))
                .build();
        try {
            snsClient.getObject().publish(request);
        } catch (SdkException e) {
            throw new SmsSendException("SNS 발송 실패: " + e.getMessage(), e);
        }
    }
}
