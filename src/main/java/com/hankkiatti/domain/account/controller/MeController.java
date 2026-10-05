package com.hankkiatti.domain.account.controller;

import com.hankkiatti.domain.account.dto.request.MeSettingsUpdateRequestDto;
import com.hankkiatti.domain.account.dto.response.MeResponseDto;
import com.hankkiatti.domain.account.service.AccountService;
import com.hankkiatti.global.response.ApiResponse;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.response.type.SuccessType;
import com.hankkiatti.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController implements MeControllerDocs {

    private final AccountService accountService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResult<MeResponseDto>> getMe(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.of(SuccessType.SUCCESS, accountService.getMe(principal.accountId()));
    }

    @Override
    @PatchMapping("/settings")
    public ResponseEntity<ApiResult<MeResponseDto>> updateSettings(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody MeSettingsUpdateRequestDto request) {
        return ApiResponse.of(SuccessType.SUCCESS, accountService.updateSettings(principal.accountId(), request));
    }
}
