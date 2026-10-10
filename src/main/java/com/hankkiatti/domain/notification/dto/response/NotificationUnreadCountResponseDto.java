package com.hankkiatti.domain.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "안 읽은 알림 개수")
public record NotificationUnreadCountResponseDto(
        @Schema(description = "안 읽은 알림 개수", example = "3") long count
) {}
