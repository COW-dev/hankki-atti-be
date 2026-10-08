package com.hankkiatti.domain.helprequest.controller.client;

import com.hankkiatti.domain.helprequest.dto.response.TimeOptionDateResponseDto;
import com.hankkiatti.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "도우미 신청", description = "장애학생이 식사 도우미를 신청하는 API")
public interface HelpRequestControllerDocs {

    @Operation(summary = "시작 시각 선택지 조회", description = """
            지금 신청할 수 있는 날짜와 그날 고를 수 있는 시작 시각을 날짜·시각 순으로 준다.
            기간은 오늘부터 7일 뒤까지이고, 주말·공휴일은 빠진다. 오늘은 아직 시작 전인 시각만 들어간다.
            고를 시각이 하나도 없는 날짜(예: 17:30이 지난 오늘)는 목록에 없다.
            시작 시각은 점심 11:30·12:00·12:30·13:00, 저녁 17:00·17:30이고 이용은 1시간 고정(endAt).""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "비밀번호 변경 필요")
    ResponseEntity<ApiResult<List<TimeOptionDateResponseDto>>> getTimeOptions();
}
