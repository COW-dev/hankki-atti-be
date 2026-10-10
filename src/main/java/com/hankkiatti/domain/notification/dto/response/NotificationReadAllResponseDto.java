package com.hankkiatti.domain.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "모두 읽음 결과")
public record NotificationReadAllResponseDto(
        @Schema(description = "이번에 읽음으로 바뀐 알림 개수", example = "3") int readCount
) {}
