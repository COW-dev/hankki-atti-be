package com.hankkiatti.domain.auth.dto.request;

import com.hankkiatti.domain.auth.validation.Password;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "비밀번호 재설정 완료 요청")
public record PasswordResetConfirmRequestDto(

        @NotBlank
        @Schema(description = "메일 링크의 #token= 값", example = "q1w2e3r4t5y6u7i8o9p0a1s2d3f4g5h6j7k8l9z0x1c")
        String token,

        @NotBlank
        @Password
        @Schema(description = "새 비밀번호 (8~64자, 영문·숫자·특수문자 각 1개 이상)", example = "hankki!2026")
        String newPassword
) {}
