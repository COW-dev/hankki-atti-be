package com.hankkiatti.domain.helprequest.controller.client;

import com.hankkiatti.domain.helprequest.dto.request.HelpRequestCreateRequestDto;
import com.hankkiatti.domain.helprequest.dto.response.HelpRequestCreateResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.MyHelpRequestResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.MyHelpRequestsResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.OpenHelpRequestDateResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.TimeOptionDateResponseDto;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "도우미 신청", description = "장애학생이 식사 도우미를 신청하고, 도우미가 지원할 신청을 보는 API")
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

    @Operation(summary = "요청 목록 조회 (도우미)", description = """
            도우미 요청 목록(F-06). 지원할 수 있는 신청(모집 중·매칭 완료, 아직 시작 전)을 날짜별로 묶어 시작 시각 순으로 준다.
            from·to는 ISO 날짜(2026-10-12)이고 to를 포함한다. from이 없으면 오늘, to가 없으면 from + 7일. from > to이거나 31일을 넘으면 422.
            블라인드 — 신청 ID·시각·도움 유형만 있다. 장애학생 정보, 기타 도움 내용, 메모, 예비 인원은 주지 않는다.
            카드마다 지금 지원하면 어떻게 되는지(applyOutcome)를 준다: 모집 중이면 MATCH(바로 매칭), 매칭 완료면 WAITING(예비 등록).
            BLOCKED(지원 불가)와 이유 blockReason: ALREADY_APPLIED — 이 신청에 내 진행 중 지원(매칭·승격 응답 대기·예비)이 있음,
            TIME_OVERLAP — 내 확정 매칭(매칭 완료·승격 응답 대기)과 이용 시간이 겹침. 내 예비와 겹치는 건 막지 않는다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공 (빈 날짜는 없다)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "COMMON_INVALID_REQUEST — 날짜 형식이 틀림")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "장애학생 계정이거나 비밀번호 변경 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "HELP_REQUEST_INVALID_DATE_RANGE — from > to 이거나 31일 초과")
    ResponseEntity<ApiResult<List<OpenHelpRequestDateResponseDto>>> getOpenRequests(
            @Parameter(hidden = true) AuthPrincipal principal,
            @Parameter(description = "조회 시작 날짜 (기본 오늘)", example = "2026-10-12") LocalDate from,
            @Parameter(description = "조회 끝 날짜, 포함 (기본 from + 7일)", example = "2026-10-18") LocalDate to);

    @Operation(summary = "신청 철회", description = """
            모집 중인 내 신청을 바로 철회한다 (확인 단계 없음). 철회된 신청은 취소 완료가 되어 내 신청의 지난 신청으로 간다.
            응답은 철회된 신청이고 내 신청 조회(GET /api/help-requests/me)의 항목과 같은 모양이다.
            모집 중이 아니거나(이미 매칭·철회·실패) 식사가 이미 시작됐으면 409.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "철회 완료 (status: CANCELED)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "도우미 계정이거나 비밀번호 변경 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "HELP_REQUEST_NOT_FOUND — 없는 신청이거나 내 신청이 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "HELP_REQUEST_INVALID_STATUS — 모집 중이 아니거나 식사가 이미 시작됨")
    ResponseEntity<ApiResult<MyHelpRequestResponseDto>> withdraw(@Parameter(hidden = true) AuthPrincipal principal,
                                                                @Parameter(description = "신청 ID") Long helpRequestId);

    @Operation(summary = "매칭 취소 (장애학생)", description = """
            매칭 완료된 내 신청을 바로 취소한다. 사유를 받지 않고 패널티도 없다. 신청은 취소(CANCELED, cancelType STUDENT_CANCEL)가 되고,
            매칭·승격 응답 대기·예비 도우미의 지원은 모두 학생 사정 취소(STUDENT_CANCELED)가 된다. 도우미들에게는 알림이 간다.
            모집 중인 신청은 신청 철회(/withdraw)로 한다. 식사가 시작된 뒤에는 취소할 수 없다 — 도우미가 오지 않았으면 노쇼 신고를 한다.
            응답은 취소된 신청이고 내 신청 조회의 항목과 같은 모양이다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "취소 완료 (status: CANCELED)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "도우미 계정이거나 비밀번호 변경 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "HELP_REQUEST_NOT_FOUND — 없는 신청이거나 내 신청이 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "HELP_REQUEST_INVALID_STATUS — 매칭 완료가 아님(모집 중이면 철회로, 이미 취소 등) 또는 식사가 시작됨")
    ResponseEntity<ApiResult<MyHelpRequestResponseDto>> cancelMatched(@Parameter(hidden = true) AuthPrincipal principal,
                                                                     @Parameter(description = "신청 ID") Long helpRequestId);

    @Operation(summary = "노쇼 신고", description = """
            이용 완료 후 24시간(정각 포함)까지 "도우미가 오지 않았어요"를 신고한다. 신청과 도우미 지원이 모두 노쇼가 되고 도우미 봉사시간은 0이 된다.
            센터 승인 없이 바로 바뀌고, 센터는 취소·노쇼 이력을 보고 사후에 판단한다.
            응답은 노쇼로 바뀐 신청(도우미 이름·카톡 ID 포함)이고 내 신청 조회의 항목과 같은 모양이다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "신고 완료 (status: NO_SHOW)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "도우미 계정이거나 비밀번호 변경 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "HELP_REQUEST_NOT_FOUND — 없는 신청이거나 내 신청이 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "HELP_REQUEST_INVALID_STATUS — 이용 완료가 아님(이미 노쇼 등), HELP_REQUEST_NO_SHOW_PERIOD_EXPIRED — 24시간 지남")
    ResponseEntity<ApiResult<MyHelpRequestResponseDto>> reportNoShow(@Parameter(hidden = true) AuthPrincipal principal,
                                                                    @Parameter(description = "신청 ID") Long helpRequestId);
}
