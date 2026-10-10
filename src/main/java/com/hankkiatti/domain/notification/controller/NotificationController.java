package com.hankkiatti.domain.notification.controller;

import com.hankkiatti.domain.notification.dto.response.NotificationReadAllResponseDto;
import com.hankkiatti.domain.notification.dto.response.NotificationResponseDto;
import com.hankkiatti.domain.notification.dto.response.NotificationUnreadCountResponseDto;
import com.hankkiatti.domain.notification.dto.response.NotificationsResponseDto;
import com.hankkiatti.domain.notification.service.NotificationService;
import com.hankkiatti.global.response.ApiResponse;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.response.type.SuccessType;
import com.hankkiatti.global.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController implements NotificationControllerDocs {

    private final NotificationService notificationService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResult<NotificationsResponseDto>> getMyNotifications(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "" + NotificationService.DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.of(SuccessType.SUCCESS,
                notificationService.getMyNotifications(principal.accountId(), cursor, size));
    }

    @Override
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResult<NotificationUnreadCountResponseDto>> countUnread(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.of(SuccessType.SUCCESS, notificationService.countUnread(principal.accountId()));
    }

    @Override
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResult<NotificationResponseDto>> markRead(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long notificationId) {
        return ApiResponse.of(SuccessType.SUCCESS, notificationService.markRead(principal.accountId(), notificationId));
    }

    @Override
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResult<NotificationReadAllResponseDto>> markAllRead(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.of(SuccessType.SUCCESS, notificationService.markAllRead(principal.accountId()));
    }
}
