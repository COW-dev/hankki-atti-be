package com.hankkiatti.domain.helprequest.dto.request;

import com.hankkiatti.domain.helprequest.entity.HelpType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Set;

@Schema(description = "도우미 신청 요청")
public record HelpRequestCreateRequestDto(

        @NotNull
        @Schema(description = "식사 시작 시각. 시작 시각 선택지(time-options)의 startAt 중 하나", example = "2026-10-12T12:00:00")
        LocalDateTime startAt,

        @NotEmpty
        @Schema(description = "필요한 도움 (1개 이상)", example = "[\"SERVING\", \"OTHER\"]")
        Set<@NotNull HelpType> helpTypes,

        @Size(max = 100)
        @Schema(description = "기타 도움 내용. 기타(OTHER)를 고르면 필수, 고르지 않으면 저장하지 않는다", example = "식판 반납")
        String otherHelpText,

        @Size(max = 200)
        @Schema(description = "도우미에게 알려 줄 내용 (선택)", example = "학생식당 출입구에서 기다릴게요")
        String memo
) {}
