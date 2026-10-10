package com.hankkiatti.domain.notification.dto.response;

import com.hankkiatti.domain.notification.entity.NotificationTargetType;
import com.hankkiatti.domain.notification.entity.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "알림")
public record NotificationResponseDto(
        @Schema(description = "알림 ID") Long id,
        @Schema(description = "알림 종류", example = "APPLICATION_MATCHED") NotificationType type,
        @Schema(description = "알림 문장", example = "10월 12일 12:00 식사 도우미로 매칭됐어요.") String message,
        @Schema(description = "누르면 이동할 대상 종류", example = "APPLICATION") NotificationTargetType targetType,
        @Schema(description = "이동할 대상 ID (신청·지원·공지 ID)", example = "31") Long targetId,
        @Schema(description = "읽었는지") boolean read,
        @Schema(description = "알림 시각", example = "2026-10-10T15:30:00") LocalDateTime createdAt
) {}
