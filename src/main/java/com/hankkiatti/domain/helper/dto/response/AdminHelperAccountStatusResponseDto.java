package com.hankkiatti.domain.helper.dto.response;

import com.hankkiatti.domain.account.entity.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "도우미 계정 상태")
public record AdminHelperAccountStatusResponseDto(
        @Schema(description = "도우미 계정 ID") Long helperId,
        @Schema(description = "계정 상태", example = "ACTIVE") AccountStatus status
) {}
