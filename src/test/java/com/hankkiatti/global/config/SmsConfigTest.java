package com.hankkiatti.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.sms.service.SmsProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.sns.SnsClient;

class SmsConfigTest {

    @Test
    void snsClient_설정한호출시간제한이적용된다() {
        // given
        SmsProperties properties = new SmsProperties(
                new SmsProperties.Sns(true, "ap-northeast-2", Duration.ofSeconds(3), Duration.ofSeconds(10)),
                new SmsProperties.Outbox(20, List.of(Duration.ofMinutes(1)), Duration.ofMinutes(5)));

        // when
        try (SnsClient client = new SmsConfig().snsClient(properties)) {
            ClientOverrideConfiguration override = client.serviceClientConfiguration().overrideConfiguration();

            // then
            assertThat(override.apiCallAttemptTimeout()).contains(Duration.ofSeconds(3));
            assertThat(override.apiCallTimeout()).contains(Duration.ofSeconds(10));
        }
    }
}
