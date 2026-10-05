package com.hankkiatti.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "로그인 요청")
public record LoginRequestDto(

        @NotBlank
        @Schema(description = "아이디 (장애학생은 학번, 도우미는 이메일, 관리자는 발급 아이디)", example = "60231234")
        String loginId,

        @NotBlank
        @Schema(description = "비밀번호", example = "hankki!2026")
        String password
) {}
