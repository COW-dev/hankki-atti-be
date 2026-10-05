package com.hankkiatti.domain.admin.controller;

import com.hankkiatti.domain.admin.dto.response.AdminMeResponseDto;
import com.hankkiatti.domain.admin.service.AdminService;
import com.hankkiatti.global.response.ApiResponse;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.response.type.SuccessType;
import com.hankkiatti.global.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/me")
@RequiredArgsConstructor
public class AdminMeController implements AdminMeControllerDocs {

    private final AdminService adminService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResult<AdminMeResponseDto>> getMe(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.of(SuccessType.SUCCESS, adminService.getMe(principal.accountId()));
    }
}
