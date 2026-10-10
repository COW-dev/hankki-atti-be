package com.hankkiatti.domain.notification.service;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 알림 작업 처리 설정 (메일 아웃박스와 같은 뜻).
 */
@ConfigurationProperties("notification.job")
public record NotificationJobProperties(
        // 폴러가 한 번에 꺼내는 최대 건수
        @DefaultValue("50") int batchSize,
        // 실패 후 다시 시도할 간격. 개수만큼 재시도하고 그래도 실패면 FAILED
        @DefaultValue({"1m", "1m", "1m"}) List<Duration> retryDelays,
        // 선점 후 이 시간이 지나도 PROCESSING이면 처리 중 서버가 죽은 것으로 보고 다시 처리한다
        @DefaultValue("5m") Duration claimTimeout
) {}
