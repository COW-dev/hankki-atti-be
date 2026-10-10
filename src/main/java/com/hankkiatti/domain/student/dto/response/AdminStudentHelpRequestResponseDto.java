package com.hankkiatti.domain.student.dto.response;

import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.entity.RequestCancelType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "장애학생 상세 · 매칭현황 탭 한 줄")
public record AdminStudentHelpRequestResponseDto(
        @Schema(description = "신청 ID") Long id,
        @Schema(description = "식사 시작 시각", example = "2026-10-12T12:00:00") LocalDateTime startAt,
        @Schema(description = "끝 시각 (시작 + 1시간)", example = "2026-10-12T13:00:00") LocalDateTime endAt,
        @Schema(description = "신청 상태", example = "COMPLETED") HelpRequestStatus status,
        @Schema(description = "필요한 도움 (배식·좌석·이동·기타 순)", example = "[\"SERVING\"]") List<HelpType> helpTypes,
        @Schema(description = "취소된 신청이면 취소 구분", example = "STUDENT_CANCEL") RequestCancelType cancelType,
        @Schema(description = "확정된 도우미. 매칭 완료·이용 완료·노쇼일 때만 있다") AdminStudentHelperResponseDto helper,
        @Schema(description = "확정된 도우미가 예비에서 승격됐으면 그때 예비 순번 (\"예비 1번 → 승격\"). 바로 매칭이거나 기록 전 승격이면 없다", example = "1") Integer promotedWaitingOrder,
        @Schema(description = "확정된 도우미가 예비에서 승격된 도우미인지") boolean promoted,
        @Schema(description = "예비가 승격돼 도우미 응답을 기다리는 중 (그동안 helper는 없다)") boolean promotionPending,
        @Schema(description = "도우미가 바뀐 적 있음") boolean helperChanged,
        @Schema(description = "신청 시각", example = "2026-10-10T09:12:00") LocalDateTime requestedAt,
        @Schema(description = "처음 매칭된 시각. 매칭된 적 없으면 없다", example = "2026-10-10T09:20:00") LocalDateTime firstMatchedAt
) {}
