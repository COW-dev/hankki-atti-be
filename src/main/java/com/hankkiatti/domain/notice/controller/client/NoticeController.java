package com.hankkiatti.domain.notice.controller.client;

import com.hankkiatti.domain.notice.dto.response.NoticeDetailResponseDto;
import com.hankkiatti.domain.notice.dto.response.NoticesResponseDto;
import com.hankkiatti.domain.notice.service.NoticeService;
import com.hankkiatti.global.response.ApiResponse;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.response.type.SuccessType;
import com.hankkiatti.global.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController implements NoticeControllerDocs {

    private final NoticeService noticeService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResult<NoticesResponseDto>> getNotices(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.of(SuccessType.SUCCESS, noticeService.getNotices(principal.accountId(), principal.role(), page));
    }

    @Override
    @GetMapping("/{noticeId}")
    public ResponseEntity<ApiResult<NoticeDetailResponseDto>> getNotice(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long noticeId) {
        return ApiResponse.of(SuccessType.SUCCESS,
                noticeService.getNotice(principal.accountId(), principal.role(), noticeId));
    }
}
