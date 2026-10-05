package com.hankkiatti.domain.mail.service;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 메일 발송 설정. SMTP 접속 정보는 Spring 기본 설정(spring.mail.*)을 쓴다.
 */
@ConfigurationProperties("mail")
public record MailProperties(
        @DefaultValue("no-reply@hankki-atti.local") String fromAddress,
        @DefaultValue("한끼아띠 (명지대학교 장애학생지원센터)") String fromName,
        @DefaultValue Outbox outbox
) {

    public record Outbox(
            // 폴러가 한 번에 꺼내는 최대 건수
            @DefaultValue("20") int batchSize,
            // 실패 후 다시 시도할 간격. 개수만큼 재시도하고 그래도 실패면 FAILED
            @DefaultValue({"1m", "1m", "1m"}) List<Duration> retryDelays,
            // 선점 후 이 시간이 지나도 SENDING이면 발송 중 서버가 죽은 것으로 보고 다시 보낸다
            @DefaultValue("5m") Duration claimTimeout
    ) {}
}
