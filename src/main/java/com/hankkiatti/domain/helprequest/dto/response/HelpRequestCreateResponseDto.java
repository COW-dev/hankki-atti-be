package com.hankkiatti.domain.helprequest.dto.response;

import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "도우미 신청 결과 (완료 화면 요약 카드)")
public record HelpRequestCreateResponseDto(
        @Schema(description = "신청 ID") Long id,
        @Schema(description = "식사 시작 시각", example = "2026-10-12T12:00:00") LocalDateTime startAt,
        @Schema(description = "끝 시각 (시작 + 1시간)", example = "2026-10-12T13:00:00") LocalDateTime endAt,
        @Schema(description = "필요한 도움 (배식·좌석·이동·기타 순)", example = "[\"SERVING\", \"OTHER\"]") List<HelpType> helpTypes,
        @Schema(description = "기타 도움 내용. 기타를 골랐을 때만 있다", example = "식판 반납") String otherHelpText,
        @Schema(description = "도우미에게 알려 줄 내용", example = "학생식당 출입구에서 기다릴게요") String memo,
        @Schema(description = "신청 상태. 처음엔 모집 중", example = "RECRUITING") HelpRequestStatus status
) {}
