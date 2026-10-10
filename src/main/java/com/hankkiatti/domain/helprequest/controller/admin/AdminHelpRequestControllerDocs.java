package com.hankkiatti.domain.helprequest.controller.admin;

import com.hankkiatti.domain.helprequest.dto.response.AdminHelpRequestResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.AdminHelpRequestRow;
import com.hankkiatti.domain.helprequest.dto.response.AdminHelpRequestSummaryResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.LimitedAdminHelpRequestResponseDto;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "관리자 - 전체 신청 현황", description = "모든 신청 건 조회 (조회 전용 — 관리자는 매칭을 바꾸지 않는다)")
public interface AdminHelpRequestControllerDocs {

    @Operation(summary = "전체 신청 현황", description = """
            기간(식사 날짜, 기본 오늘~7일 뒤, 최대 31일) 안의 신청을 식사 일시 오름차순으로 준다. 상태·검색어(장애학생 이름·학번 부분 일치)로 거를 수 있다.
            전체 권한은 장애학생 장애 유형(student.disabilityType)을 포함하고, 제한 권한 응답에는 그 필드 자체가 없다.
            helper는 확정된 도우미(매칭 완료·이용 완료·노쇼)만, 승격 응답을 기다리는 중이면 promotionPending=true이고 helper는 없다.
            minutesToMatch = 신청 → 첫 매칭까지 걸린 분.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공 (항목은 등급에 따라 두 모양 중 하나)",
            content = @Content(array = @ArraySchema(schema = @Schema(oneOf = {AdminHelpRequestResponseDto.class,
                    LimitedAdminHelpRequestResponseDto.class}))))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "COMMON_INVALID_REQUEST — 날짜·상태 값 형식이 틀림")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자가 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "HELP_REQUEST_INVALID_DATE_RANGE — 시작이 끝보다 늦거나 31일을 넘음")
    ResponseEntity<ApiResult<List<AdminHelpRequestRow>>> getHelpRequests(
            @Parameter(hidden = true) AuthPrincipal principal,
            @Parameter(description = "시작 날짜 (기본 오늘)", example = "2026-10-12") LocalDate from,
            @Parameter(description = "끝 날짜, 포함 (기본 시작 + 7일)", example = "2026-10-19") LocalDate to,
            @Parameter(description = "신청 상태 (없으면 전체)") HelpRequestStatus status,
            @Parameter(description = "장애학생 이름·학번 검색어 (부분 일치)") String q);

    @Operation(summary = "전체 신청 현황 요약 숫자", description = """
            선택 기간의 신청 / 매칭 완료 / 모집 중 / 매칭 실패 / 노쇼 건수. 상태 필터·검색어와 상관없이 기간 전체를 센다.
            신청 = 기간 안 모든 신청(철회·취소 포함), 매칭 완료 = 매칭 완료 + 이용 완료.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자가 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "HELP_REQUEST_INVALID_DATE_RANGE — 시작이 끝보다 늦거나 31일을 넘음")
    ResponseEntity<ApiResult<AdminHelpRequestSummaryResponseDto>> getSummary(
            @Parameter(hidden = true) AuthPrincipal principal,
            @Parameter(description = "시작 날짜 (기본 오늘)") LocalDate from,
            @Parameter(description = "끝 날짜, 포함 (기본 시작 + 7일)") LocalDate to);
}
