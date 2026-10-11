package com.hankkiatti.domain.application.controller.admin;

import com.hankkiatti.domain.application.dto.request.VolunteerHoursUpdateRequestDto;
import com.hankkiatti.domain.application.dto.response.AdminVolunteerHoursResponseDto;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "관리자 - 지원", description = "도우미 지원 건 관리 (봉사시간 수정)")
public interface AdminApplicationControllerDocs {

    @Operation(summary = "봉사시간 수정", description = """
            이용 완료·노쇼 건의 봉사시간을 0.5시간 단위(0~10)로 고치고 이력(이전·새 시간, 사유, 관리자, 시각)을 남긴다. 사유 필수.
            전체 권한만. 상태는 그대로 두고 시간만 바꾸며, 도우미 봉사시간 합계는 지원 행의 합이라 저절로 반영된다.
            이력은 도우미 활동 이력(GET /api/admin/helpers/{id}/activities)의 volunteerHourChanges로 보인다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "AUTH_ACCESS_DENIED — 관리자가 아니거나 제한 권한")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "APPLICATION_NOT_FOUND — 없는 지원")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "APPLICATION_INVALID_STATUS — 이용 완료·노쇼가 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = """
            COMMON_VALIDATION_FAILED — 시간 없음·0~10 밖·사유 없음·200자 초과 / APPLICATION_INVALID_VOLUNTEER_HOURS — 0.5 단위 아님 /
            APPLICATION_VOLUNTEER_HOURS_UNCHANGED — 지금 값과 같음""")
    ResponseEntity<ApiResult<AdminVolunteerHoursResponseDto>> changeVolunteerHours(
            @Parameter(hidden = true) AuthPrincipal principal,
            @Parameter(description = "지원 ID") Long applicationId,
            VolunteerHoursUpdateRequestDto request);
}
