package com.hankkiatti.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "비밀번호 재설정 메일 요청")
public record PasswordResetRequestDto(

        @NotBlank
        @Schema(description = "아이디 (장애학생은 학번, 도우미는 이메일)", example = "60231234")
        String loginId
) {}
