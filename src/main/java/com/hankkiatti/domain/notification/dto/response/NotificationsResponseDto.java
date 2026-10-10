package com.hankkiatti.domain.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "알림 목록 한 페이지 (최신순)")
public record NotificationsResponseDto(
        @Schema(description = "알림") List<NotificationResponseDto> items,
        @Schema(description = "다음 페이지를 받을 때 cursor에 넣을 값. 더 없으면 null", example = "120") Long nextCursor
) {}
