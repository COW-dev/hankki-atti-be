package com.hankkiatti.domain.helprequest.dto.response;

import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "내 신청 하나")
public record MyHelpRequestResponseDto(
        @Schema(description = "신청 ID") Long id,
        @Schema(description = "식사 시작 시각", example = "2026-10-12T12:00:00") LocalDateTime startAt,
        @Schema(description = "끝 시각 (시작 + 1시간)", example = "2026-10-12T13:00:00") LocalDateTime endAt,
        @Schema(description = "신청 상태", example = "MATCHED") HelpRequestStatus status,
        @Schema(description = "필요한 도움 (배식·좌석·이동·기타 순)", example = "[\"SERVING\", \"SEATING\"]") List<HelpType> helpTypes,
        @Schema(description = "기타 도움 내용. 기타를 골랐을 때만 있다") String otherHelpText,
        @Schema(description = "도우미에게 알려 줄 내용") String memo,
        @Schema(description = "매칭된 도우미. 매칭 완료·이용 완료·노쇼일 때만 있고 그 밖에는 null") MatchedHelperResponseDto helper,
        @Schema(description = "예비 승격으로 도우미가 바뀐 적 있음 (\"도우미 바뀜\" 표시)") boolean helperChanged,
        @Schema(description = "지금 노쇼 신고를 할 수 있는지 (이용 완료 후 24시간까지)") boolean noShowReportable,
        @Schema(description = "노쇼 신고 마감 시각. 신고할 수 있을 때만 있다", example = "2026-10-13T13:00:00") LocalDateTime noShowDeadline
) {}
