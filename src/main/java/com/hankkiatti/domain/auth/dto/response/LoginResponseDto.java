package com.hankkiatti.domain.auth.dto.response;

import com.hankkiatti.domain.account.entity.AccountRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 앱 로그인 응답. refresh 토큰은 HttpOnly 쿠키로 내려간다")
public record LoginResponseDto(
        @Schema(description = "access 토큰 (Authorization: Bearer)") String accessToken,
        @Schema(description = "access 토큰 유효 시간(초)", example = "1800") long expiresIn,
        @Schema(description = "역할", example = "STUDENT") AccountRole role,
        @Schema(description = "true면 비밀번호 변경 화면으로 보낸다. 변경 전에는 비밀번호 변경 API만 쓸 수 있다") boolean mustChangePassword
) {}
