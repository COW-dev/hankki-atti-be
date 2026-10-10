package com.hankkiatti.domain.notification.controller;

import com.hankkiatti.domain.notification.dto.response.NotificationReadAllResponseDto;
import com.hankkiatti.domain.notification.dto.response.NotificationResponseDto;
import com.hankkiatti.domain.notification.dto.response.NotificationUnreadCountResponseDto;
import com.hankkiatti.domain.notification.dto.response.NotificationsResponseDto;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "알림", description = "장애학생·도우미의 인앱 알림 (관리자는 알림 없음)")
public interface NotificationControllerDocs {

    @Operation(summary = "내 알림 목록", description = """
            내 알림을 최신순으로 준다. 처음에는 cursor 없이 부르고, 다음 페이지는 응답의 nextCursor를 cursor에 넣어 부른다.
            nextCursor가 null이면 더 없다. 그사이 새 알림이 와도 다음 페이지가 밀리지 않는다 (새 알림은 첫 페이지를 다시 부르면 나온다).
            targetType·targetId는 누르면 이동할 대상이다: HELP_REQUEST(장애학생 신청), APPLICATION(도우미 지원), NOTICE(공지).""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "COMMON_INVALID_REQUEST — cursor·size가 숫자가 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 계정이거나 비밀번호 변경 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "NOTIFICATION_INVALID_PAGE_SIZE — size가 1~50 밖")
    ResponseEntity<ApiResult<NotificationsResponseDto>> getMyNotifications(
            @Parameter(hidden = true) AuthPrincipal principal,
            @Parameter(description = "앞 페이지의 nextCursor (첫 페이지는 비움)") Long cursor,
            @Parameter(description = "한 페이지 개수 (기본 20, 최대 50)") int size);

    @Operation(summary = "안 읽은 알림 개수", description = "알림 버튼 배지용. 화면이 주기적으로 불러도 가볍게 센다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 계정이거나 비밀번호 변경 필요")
    ResponseEntity<ApiResult<NotificationUnreadCountResponseDto>> countUnread(
            @Parameter(hidden = true) AuthPrincipal principal);

    @Operation(summary = "알림 읽음", description = "알림 하나를 읽음으로 바꾸고 바뀐 알림을 준다. 이미 읽은 알림이면 그대로 준다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "읽음 처리 (read: true)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 계정이거나 비밀번호 변경 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOTIFICATION_NOT_FOUND — 없는 알림이거나 내 알림이 아님")
    ResponseEntity<ApiResult<NotificationResponseDto>> markRead(@Parameter(hidden = true) AuthPrincipal principal,
                                                                @Parameter(description = "알림 ID") Long notificationId);

    @Operation(summary = "모두 읽음", description = "안 읽은 내 알림을 모두 읽음으로 바꾸고, 이번에 바뀐 개수를 준다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "모두 읽음 처리")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 계정이거나 비밀번호 변경 필요")
    ResponseEntity<ApiResult<NotificationReadAllResponseDto>> markAllRead(
            @Parameter(hidden = true) AuthPrincipal principal);
}
