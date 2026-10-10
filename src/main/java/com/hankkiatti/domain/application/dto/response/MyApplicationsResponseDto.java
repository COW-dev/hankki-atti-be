package com.hankkiatti.domain.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "매칭 현황 (도우미 홈)")
public record MyApplicationsResponseDto(
        @Schema(description = "진행 중 — 매칭 완료·승격 응답 대기·예비, 식사 시각 가까운 순") List<MyApplicationResponseDto> inProgress,
        @Schema(description = "지난 활동 — 이용 완료·노쇼·취소·예비 종료·자동 제외·빠짐·승격 거절, 최근 순") List<MyApplicationResponseDto> past
) {}
