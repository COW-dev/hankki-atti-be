package com.hankkiatti.domain.helprequest.controller.client;

import com.hankkiatti.domain.helprequest.dto.request.HelpRequestCreateRequestDto;
import com.hankkiatti.domain.helprequest.dto.response.HelpRequestCreateResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.MyHelpRequestsResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.TimeOptionDateResponseDto;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

    @Operation(summary = "도우미 신청", description = """
            장애학생이 날짜·시작 시각·필요한 도움을 골라 신청한다. 신청은 모집 중으로 시작한다.
            startAt은 시작 시각 선택지(time-options)에 있는 시각이어야 한다 (지난 시각·주말·공휴일·7일 넘음이면 422).
            내 모집 중·매칭 완료 신청과 이용 시간(1시간)이 겹치면 막는다 (409). 12:00과 12:30은 겹치고 12:00과 13:00은 겹치지 않는다.
            기타(OTHER)를 고르면 otherHelpText가 필요하다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "신청 완료 (모집 중)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "도우미 계정이거나 비밀번호 변경 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "HELP_REQUEST_TIME_OVERLAP — 이미 신청한 시간과 겹침")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "HELP_REQUEST_START_TIME_NOT_AVAILABLE — 고를 수 없는 시각, HELP_REQUEST_OTHER_HELP_TEXT_REQUIRED — 기타 내용 없음, COMMON_VALIDATION_FAILED — 도움 유형 없음·글자 수 초과")
    ResponseEntity<ApiResult<HelpRequestCreateResponseDto>> create(@Parameter(hidden = true) AuthPrincipal principal,
                                                                  HelpRequestCreateRequestDto request);

    @Operation(summary = "내 신청 조회", description = """
            장애학생 홈(F-02)의 다가오는 신청과 지난 신청을 준다.
            다가오는 신청은 모집 중·매칭 완료로 시작 시각이 가까운 순, 지난 신청은 매칭 실패·취소 완료·이용 완료·노쇼로 최근 순이다.
            매칭 완료·이용 완료·노쇼 신청에는 도우미 이름·카톡 ID가 들어간다 (전화번호는 없다). 예비 명단은 주지 않는다.
            이용 완료 후 24시간까지는 noShowReportable이 true이고 noShowDeadline에 마감 시각이 있다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "도우미 계정이거나 비밀번호 변경 필요")
    ResponseEntity<ApiResult<MyHelpRequestsResponseDto>> getMyRequests(@Parameter(hidden = true) AuthPrincipal principal);
}
