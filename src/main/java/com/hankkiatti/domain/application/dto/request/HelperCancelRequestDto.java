package com.hankkiatti.domain.application.dto.request;

import com.hankkiatti.domain.application.entity.CancelReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "도우미 매칭 취소 요청")
public record HelperCancelRequestDto(

        @NotNull
        @Schema(description = "취소 사유 (ADMIN_DEACTIVATED는 고를 수 없다)", example = "ILLNESS")
        CancelReason reason,

        @Size(max = 200)
        @Schema(description = "기타 사유 내용. reason이 OTHER면 필수", example = "갑자기 시험 일정이 생겼어요")
        String reasonDetail
) {}
