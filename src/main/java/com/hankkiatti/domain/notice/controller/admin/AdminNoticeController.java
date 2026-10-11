package com.hankkiatti.domain.notice.controller.admin;

import com.hankkiatti.domain.notice.dto.request.AdminNoticeSaveRequestDto;
import com.hankkiatti.domain.notice.dto.response.AdminNoticeDeleteResponseDto;
import com.hankkiatti.domain.notice.dto.response.AdminNoticeDetailResponseDto;
import com.hankkiatti.domain.notice.dto.response.AdminNoticesResponseDto;
import com.hankkiatti.domain.notice.service.AdminNoticeService;
import com.hankkiatti.global.response.ApiResponse;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.response.type.SuccessType;
import com.hankkiatti.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController implements AdminNoticeControllerDocs {

    private final AdminNoticeService adminNoticeService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResult<AdminNoticesResponseDto>> getNotices(
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.of(SuccessType.SUCCESS, adminNoticeService.getNotices(page));
    }

    @Override
    @GetMapping("/{noticeId}")
    public ResponseEntity<ApiResult<AdminNoticeDetailResponseDto>> getNotice(@PathVariable Long noticeId) {
        return ApiResponse.of(SuccessType.SUCCESS, adminNoticeService.getNotice(noticeId));
    }

    @Override
    @PostMapping
    public ResponseEntity<ApiResult<AdminNoticeDetailResponseDto>> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody AdminNoticeSaveRequestDto request) {
        return ApiResponse.of(SuccessType.CREATED, adminNoticeService.create(principal.accountId(), request));
    }

    @Override
    @PatchMapping("/{noticeId}")
    public ResponseEntity<ApiResult<AdminNoticeDetailResponseDto>> update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long noticeId,
            @Valid @RequestBody AdminNoticeSaveRequestDto request) {
        return ApiResponse.of(SuccessType.SUCCESS, adminNoticeService.update(principal.accountId(), noticeId, request));
    }

    @Override
    @DeleteMapping("/{noticeId}")
    public ResponseEntity<ApiResult<AdminNoticeDeleteResponseDto>> delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long noticeId) {
        return ApiResponse.of(SuccessType.SUCCESS, adminNoticeService.delete(principal.accountId(), noticeId));
    }
}
