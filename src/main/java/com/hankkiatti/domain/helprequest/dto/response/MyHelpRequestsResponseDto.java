package com.hankkiatti.domain.helprequest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "내 신청 (F-02 홈). 다가오는 신청과 지난 신청")
public record MyHelpRequestsResponseDto(
        @Schema(description = "다가오는 신청: 모집 중·매칭 완료, 시작 시각이 가까운 순 (첫 건이 \"다음 식사\")") List<MyHelpRequestResponseDto> upcoming,
        @Schema(description = "지난 신청: 매칭 실패·취소 완료·이용 완료·노쇼, 최근 순") List<MyHelpRequestResponseDto> past
) {}
