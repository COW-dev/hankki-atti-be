package com.hankkiatti.domain.auth.dto.response;

import com.hankkiatti.domain.admin.entity.AdminGrade;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 로그인 응답. refresh 토큰은 HttpOnly 쿠키로 내려간다")
public record AdminLoginResponseDto(
        @Schema(description = "access 토큰 (Authorization: Bearer)") String accessToken,
        @Schema(description = "access 토큰 유효 시간(초)", example = "1800") long expiresIn,
        @Schema(description = "관리자 이름", example = "김센터") String name,
        @Schema(description = "권한 등급", example = "FULL") AdminGrade grade
) {}
