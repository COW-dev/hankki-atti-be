package com.hankkiatti.domain.application.controller;

import com.hankkiatti.domain.application.dto.request.HelperCancelRequestDto;
import com.hankkiatti.domain.application.dto.response.ApplyResponseDto;
import com.hankkiatti.domain.application.dto.response.HelperCancelResponseDto;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "지원", description = "도우미가 신청에 지원하고 매칭을 취소하는 API")
public interface ApplicationControllerDocs {

    @Operation(summary = "지원", description = """
            도우미가 요청 목록의 신청에 지원한다. 모집 중이면 바로 매칭(MATCHED)되고, 이미 매칭된 신청이면 지원 순서대로 예비(WAITING) N번이 된다.
            동시에 여러 명이 지원하면 서버에 먼저 도착한 순서다.
            바로 매칭이면 장애학생 이름·카톡 ID(student)를 준다. 예비면 student 없이 waitingOrder만 준다 (예비 인원 총수는 주지 않는다).
            지원할 수 있는지는 요청 목록(GET /api/help-requests/open)의 applyOutcome과 같은 규칙이다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "지원 완료 (바로 매칭 또는 예비)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "장애학생 계정이거나 비밀번호 변경 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "HELP_REQUEST_NOT_FOUND — 없는 신청")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "APPLICATION_NOT_OPEN — 모집 중·매칭 완료가 아니거나 식사가 시작됨, APPLICATION_ALREADY_APPLIED — 이 신청에 진행 중 지원이 있음, APPLICATION_TIME_OVERLAP — 매칭 완료·승격 응답 대기와 시간이 겹침")
    ResponseEntity<ApiResult<ApplyResponseDto>> apply(@Parameter(hidden = true) AuthPrincipal principal,
                                                      @Parameter(description = "신청 ID") Long helpRequestId);

    @Operation(summary = "매칭 취소 (도우미)", description = """
            매칭된 도우미가 사유를 골라 취소한다. 관리자 승인 없이 바로 처리되고 사유·시각이 이력으로 남는다.
            예비가 있으면 지원 순으로 1번이 매칭으로 승격되고(그사이 다른 건에 매칭돼 시간이 겹치는 예비는 자동 제외), 없으면 다시 모집 중이 된다.
            사유가 OTHER면 reasonDetail이 필요하다. ADMIN_DEACTIVATED는 고를 수 없다.
            식사가 시작된 뒤에는 취소할 수 없다 (예비가 이미 종료돼 승격할 사람이 없다).
            응답은 취소된 지원이다. 장애학생 정보와 승격 여부는 주지 않는다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "취소 완료 (status: HELPER_CANCELED)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "COMMON_INVALID_REQUEST — 사유 값이 목록에 없음")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "비밀번호 변경 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "APPLICATION_NOT_FOUND — 없는 지원이거나 내 지원이 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "APPLICATION_INVALID_STATUS — 매칭 완료가 아님(이미 취소 등), APPLICATION_MEAL_STARTED — 식사가 시작됨")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "COMMON_VALIDATION_FAILED — 사유 없음·내용 200자 초과, APPLICATION_CANCEL_REASON_DETAIL_REQUIRED — 기타인데 내용 없음, APPLICATION_INVALID_CANCEL_REASON — 관리자 비활성화 사유")
    ResponseEntity<ApiResult<HelperCancelResponseDto>> cancel(@Parameter(hidden = true) AuthPrincipal principal,
                                                              @Parameter(description = "지원 ID") Long applicationId,
                                                              HelperCancelRequestDto request);
}
