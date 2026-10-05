package com.hankkiatti.domain.auth.dto.request;

import com.hankkiatti.domain.auth.validation.Password;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "비밀번호 변경 요청 (첫 로그인 변경 포함)")
public record PasswordChangeRequestDto(

        @NotBlank
        @Schema(description = "현재 비밀번호 (첫 로그인이면 메일로 받은 비밀번호)", example = "temp!2026")
        String currentPassword,

        @NotBlank
        @Password
        @Schema(description = "새 비밀번호 (8~64자, 영문·숫자·특수문자 각 1개 이상)", example = "hankki!2026")
        String newPassword
) {}
