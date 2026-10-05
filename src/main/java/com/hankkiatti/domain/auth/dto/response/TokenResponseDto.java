package com.hankkiatti.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "새로 발급한 access 토큰. refresh 토큰은 HttpOnly 쿠키로 함께 교체된다")
public record TokenResponseDto(
        @Schema(description = "access 토큰 (Authorization: Bearer)") String accessToken,
        @Schema(description = "access 토큰 유효 시간(초)", example = "1800") long expiresIn
) {}
