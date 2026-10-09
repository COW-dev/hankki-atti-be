package com.hankkiatti.domain.application.controller;

import com.hankkiatti.domain.application.dto.response.ApplyResponseDto;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "지원", description = "도우미가 신청에 지원하는 API")
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
}
