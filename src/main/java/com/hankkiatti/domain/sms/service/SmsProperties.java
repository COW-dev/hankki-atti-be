package com.hankkiatti.domain.sms.service;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 문자 발송 설정. AWS 자격 증명은 SDK 기본 체인(환경 변수 AWS_ACCESS_KEY_ID·AWS_SECRET_ACCESS_KEY 등)으로 받는다.
 */
@ConfigurationProperties("sms")
public record SmsProperties(
        @DefaultValue Sns sns,
        @DefaultValue Outbox outbox
) {

    public record Sns(
            // false면 SNS 클라이언트를 만들지 않고 문자는 발송 대기로 남는다
            @DefaultValue("false") boolean enabled,
            @DefaultValue("ap-northeast-2") String region,
            // SNS 호출 한 번(시도 1회)의 최대 시간
            @DefaultValue("3s") Duration apiCallAttemptTimeout,
            // SDK 재시도까지 합친 발송 한 건의 최대 시간. 넘으면 실패로 보고 아웃박스가 다시 보낸다
            @DefaultValue("10s") Duration apiCallTimeout
    ) {}

    public record Outbox(
            // 폴러가 한 번에 꺼내는 최대 건수
            @DefaultValue("20") int batchSize,
            // 실패 후 다시 시도할 간격. 개수만큼 재시도하고 그래도 실패면 FAILED
            @DefaultValue({"1m", "1m", "1m"}) List<Duration> retryDelays,
            // 선점 후 이 시간이 지나도 SENDING이면 발송 중 서버가 죽은 것으로 보고 다시 보낸다
            @DefaultValue("5m") Duration claimTimeout
    ) {}
}
