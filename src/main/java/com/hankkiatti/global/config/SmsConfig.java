package com.hankkiatti.global.config;

import com.hankkiatti.domain.sms.service.SmsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

@Configuration
@EnableConfigurationProperties(SmsProperties.class)
public class SmsConfig {

    /**
     * AWS SNS 클라이언트. 자격 증명은 SDK 기본 체인(환경 변수·K8s 서비스 계정 등)으로 찾는다.
     * sms.sns.enabled가 아니면 만들지 않는다 — 문자는 발송 대기로 남고 서버 기동에는 영향이 없다.
     * SNS가 응답하지 않을 때 문자 스레드가 오래 묶이지 않게 호출 시간을 제한한다. 시간이 넘었는데 SNS는 받은 경우
     * 아웃박스가 다시 보내 같은 문자가 두 번 갈 수 있다 — 안 가는 것보다 낫다고 본다.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "sms.sns.enabled", havingValue = "true")
    public SnsClient snsClient(SmsProperties smsProperties) {
        SmsProperties.Sns sns = smsProperties.sns();
        return SnsClient.builder()
                .region(Region.of(sns.region()))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(sns.apiCallAttemptTimeout())
                        .apiCallTimeout(sns.apiCallTimeout())
                        .build())
                .build();
    }
}
