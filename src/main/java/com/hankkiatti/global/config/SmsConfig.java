package com.hankkiatti.global.config;

import com.hankkiatti.domain.sms.service.SmsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

@Configuration
@EnableConfigurationProperties(SmsProperties.class)
public class SmsConfig {

    /**
     * AWS SNS 클라이언트. 자격 증명은 SDK 기본 체인(환경 변수·K8s 서비스 계정 등)으로 찾는다.
     * sms.sns.enabled가 아니면 만들지 않는다 — 문자는 발송 대기로 남고 서버 기동에는 영향이 없다.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "sms.sns.enabled", havingValue = "true")
    public SnsClient snsClient(SmsProperties smsProperties) {
        return SnsClient.builder()
                .region(Region.of(smsProperties.sns().region()))
                .build();
    }
}
