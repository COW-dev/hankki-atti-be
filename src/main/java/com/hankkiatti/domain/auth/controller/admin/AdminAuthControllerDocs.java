package com.hankkiatti.domain.auth.controller.admin;

import com.hankkiatti.domain.auth.dto.request.LoginRequestDto;
import com.hankkiatti.domain.auth.dto.response.AdminLoginResponseDto;
import com.hankkiatti.domain.auth.dto.response.TokenResponseDto;
import com.hankkiatti.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

@Tag(name = "관리자 인증", description = "관리자 로그인, 토큰 재발급, 로그아웃")
public interface AdminAuthControllerDocs {

    @Operation(summary = "관리자 로그인", description = "access 토큰은 응답 본문, refresh 토큰은 HttpOnly 쿠키로 내려간다. 응답의 grade로 메뉴를 나눈다.")
    @SecurityRequirements
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "아이디 또는 비밀번호가 올바르지 않음")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "사용이 중지된 계정 (비밀번호가 맞을 때만)")
    ResponseEntity<ApiResult<AdminLoginResponseDto>> login(LoginRequestDto request);

    @Operation(summary = "관리자 토큰 재발급", description = "관리자 refresh 토큰 쿠키로 새 access 토큰을 받는다.")
    @SecurityRequirements
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재발급 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "refresh 토큰이 없거나 만료·폐기됨 → 다시 로그인")
    ResponseEntity<ApiResult<TokenResponseDto>> refresh(@Parameter(hidden = true) HttpServletRequest request);

    @Operation(summary = "관리자 로그아웃", description = "refresh 토큰을 폐기하고 쿠키를 지운다.")
    @SecurityRequirements
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공")
    ResponseEntity<ApiResult<Void>> logout(@Parameter(hidden = true) HttpServletRequest request);
}
