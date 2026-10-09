package com.hankkiatti.domain.application.controller;

import com.hankkiatti.domain.application.dto.request.HelperCancelRequestDto;
import com.hankkiatti.domain.application.dto.response.ApplyResponseDto;
import com.hankkiatti.domain.application.dto.response.HelperCancelResponseDto;
import com.hankkiatti.domain.application.service.ApplicationService;
import com.hankkiatti.domain.application.service.HelperCancelService;
import com.hankkiatti.global.response.ApiResponse;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.response.type.SuccessType;
import com.hankkiatti.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ApplicationController implements ApplicationControllerDocs {

    private final ApplicationService applicationService;
    private final HelperCancelService helperCancelService;

    @Override
    @PostMapping("/api/help-requests/{helpRequestId}/applications")
    public ResponseEntity<ApiResult<ApplyResponseDto>> apply(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long helpRequestId) {
        return ApiResponse.of(SuccessType.CREATED, applicationService.apply(principal.accountId(), helpRequestId));
    }

    @Override
    @PostMapping("/api/applications/{applicationId}/cancel")
    public ResponseEntity<ApiResult<HelperCancelResponseDto>> cancel(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long applicationId,
            @Valid @RequestBody HelperCancelRequestDto request) {
        return ApiResponse.of(SuccessType.SUCCESS,
                helperCancelService.cancel(principal.accountId(), applicationId, request));
    }
}
