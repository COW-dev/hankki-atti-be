package com.hankkiatti.domain.admin.controller;

import com.hankkiatti.domain.admin.dto.response.AdminMeResponseDto;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "관리자 내 정보", description = "로그인한 관리자 정보")
public interface AdminMeControllerDocs {

    @Operation(summary = "관리자 내 정보 조회", description = "이름과 권한 등급(전체/제한)을 확인한다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 토큰이 아님")
    ResponseEntity<ApiResult<AdminMeResponseDto>> getMe(@Parameter(hidden = true) AuthPrincipal principal);
}
