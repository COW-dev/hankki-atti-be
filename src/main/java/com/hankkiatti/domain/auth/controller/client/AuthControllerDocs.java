package com.hankkiatti.domain.auth.controller.client;

import com.hankkiatti.domain.auth.dto.request.LoginRequestDto;
import com.hankkiatti.domain.auth.dto.request.PasswordChangeRequestDto;
import com.hankkiatti.domain.auth.dto.response.LoginResponseDto;
import com.hankkiatti.domain.auth.dto.response.TokenResponseDto;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

@Tag(name = "인증", description = "장애학생·도우미 로그인, 토큰 재발급, 로그아웃, 비밀번호 변경")
public interface AuthControllerDocs {

    @Operation(summary = "로그인", description = """
            아이디(장애학생은 학번, 도우미는 이메일)와 비밀번호로 로그인한다.
            access 토큰은 응답 본문, refresh 토큰은 HttpOnly 쿠키로 내려간다.
            mustChangePassword가 true면 비밀번호 변경 전까지 비밀번호 변경 API만 쓸 수 있다.""")
    @SecurityRequirements
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "아이디 또는 비밀번호가 올바르지 않음")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "사용이 중지된 계정 (비밀번호가 맞을 때만)")
    ResponseEntity<ApiResult<LoginResponseDto>> login(LoginRequestDto request);

    @Operation(summary = "토큰 재발급", description = "refresh 토큰 쿠키로 새 access 토큰을 받는다. refresh 토큰도 새로 교체된다.")
    @SecurityRequirements
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재발급 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "refresh 토큰이 없거나 만료·폐기됨 → 다시 로그인")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "허용되지 않은 Origin (CORS 필터가 거부)")
    ResponseEntity<ApiResult<TokenResponseDto>> refresh(@Parameter(hidden = true) HttpServletRequest request);

    @Operation(summary = "로그아웃", description = "refresh 토큰을 폐기하고 쿠키를 지운다.")
    @SecurityRequirements
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "허용되지 않은 Origin (CORS 필터가 거부)")
    ResponseEntity<ApiResult<Void>> logout(@Parameter(hidden = true) HttpServletRequest request);

    @Operation(summary = "비밀번호 변경", description = """
            현재 비밀번호를 확인하고 새 비밀번호로 바꾼다. 첫 로그인 비밀번호 변경도 이 API를 쓴다.
            다른 기기의 로그인은 모두 끊기고, 지금 기기에는 새 토큰을 준다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "현재 비밀번호가 틀림")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "비밀번호 규칙 위반 또는 현재와 같은 비밀번호")
    ResponseEntity<ApiResult<TokenResponseDto>> changePassword(@Parameter(hidden = true) AuthPrincipal principal,
                                                               PasswordChangeRequestDto request);
}
