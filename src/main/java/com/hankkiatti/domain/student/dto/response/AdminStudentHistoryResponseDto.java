package com.hankkiatti.domain.student.dto.response;

import com.hankkiatti.domain.application.entity.ApplicationAfterAction;
import com.hankkiatti.domain.application.entity.CancelReason;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "장애학생 상세 · 취소·노쇼 이력 한 줄")
public record AdminStudentHistoryResponseDto(
        @Schema(description = "구분", example = "HELPER_CANCELED") AdminStudentHistoryType type,
        @Schema(description = "신청 ID") Long helpRequestId,
        @Schema(description = "식사 시작 시각", example = "2026-10-12T12:00:00") LocalDateTime startAt,
        @Schema(description = "도우미 — 도우미 취소면 취소한 도우미, 노쇼면 노쇼 처리된 도우미. 학생 취소는 없다") AdminStudentHelperResponseDto helper,
        @Schema(description = "도우미 취소 사유", example = "ACADEMIC") CancelReason cancelReason,
        @Schema(description = "기타 사유로 취소했을 때 입력 내용", example = "알바 시간이 겹쳤어요") String cancelReasonDetail,
        @Schema(description = "일어난 시각 — 취소 시각 또는 노쇼 신고 시각", example = "2026-10-11T10:00:00") LocalDateTime occurredAt,
        @Schema(description = "취소가 식사 몇 분 전이었는지 (도우미·학생 취소)", example = "1560") Long minutesBeforeMeal,
        @Schema(description = "노쇼 신고가 이용 완료 몇 분 뒤였는지 (노쇼)", example = "180") Long minutesAfterCompletion,
        @Schema(description = "도우미 취소 뒤 처리 — 예비 승격 또는 모집 재개", example = "PROMOTED") ApplicationAfterAction afterAction,
        @Schema(description = "예비 승격이면 승격된 도우미") AdminStudentHelperResponseDto promotedHelper,
        @Schema(description = "예비 승격이면 그 도우미의 승격 당시 예비 순번", example = "1") Integer promotedWaitingOrder,
        @Schema(description = "그 신청의 지금 상태 — 모집 재개 뒤 매칭 성공·실패를 보여 줄 때 쓴다", example = "FAILED") HelpRequestStatus requestStatus
) {}
