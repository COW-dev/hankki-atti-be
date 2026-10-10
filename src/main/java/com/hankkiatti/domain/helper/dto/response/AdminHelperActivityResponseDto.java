package com.hankkiatti.domain.helper.dto.response;

import com.hankkiatti.domain.application.entity.ApplicationAfterAction;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.entity.CancelReason;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "도우미 활동 이력 한 줄 — 매칭되거나 승격된 지원")
public record AdminHelperActivityResponseDto(
        @Schema(description = "지원 ID") Long applicationId,
        @Schema(description = "신청 ID") Long helpRequestId,
        @Schema(description = "식사 시작 시각", example = "2026-09-24T12:30:00") LocalDateTime startAt,
        @Schema(description = "장애학생 계정 ID") Long studentId,
        @Schema(description = "장애학생 이름 (장애 유형·연락처는 주지 않는다)", example = "김민지") String studentName,
        @Schema(description = "결과 — 매칭 완료·승격 응답 대기·승격 거절·도우미 취소·학생 취소·이용 완료·노쇼", example = "HELPER_CANCELED") ApplicationStatus status,
        @Schema(description = "도우미 취소 사유", example = "ACADEMIC") CancelReason cancelReason,
        @Schema(description = "기타 사유로 취소했을 때 입력 내용", example = "수업 보강") String cancelReasonDetail,
        @Schema(description = "취소 시각 — 도우미 취소·승격 거절은 도우미가, 학생 취소는 장애학생이 취소한 시각", example = "2026-09-23T10:30:00") LocalDateTime canceledAt,
        @Schema(description = "도우미 취소가 식사 몇 분 전이었는지", example = "1560") Long minutesBeforeMeal,
        @Schema(description = "노쇼 신고가 이용 완료 몇 분 뒤였는지", example = "180") Long minutesAfterCompletion,
        @Schema(description = "도우미 취소 뒤 처리 — 예비 승격 또는 모집 재개", example = "PROMOTED") ApplicationAfterAction afterAction,
        @Schema(description = "예비 승격이면 승격된 예비의 승격 당시 순번", example = "1") Integer promotedWaitingOrder,
        @Schema(description = "그 신청의 지금 상태 — 모집 재개 뒤 매칭 성공·실패를 보여 줄 때 쓴다", example = "MATCHED") HelpRequestStatus requestStatus
) {}
