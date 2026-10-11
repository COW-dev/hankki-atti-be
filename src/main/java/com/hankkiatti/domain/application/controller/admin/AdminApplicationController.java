package com.hankkiatti.domain.application.controller.admin;

import com.hankkiatti.domain.application.dto.request.VolunteerHoursUpdateRequestDto;
import com.hankkiatti.domain.application.dto.response.AdminVolunteerHoursResponseDto;
import com.hankkiatti.domain.application.service.AdminVolunteerHourService;
import com.hankkiatti.global.response.ApiResponse;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.response.type.SuccessType;
import com.hankkiatti.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/applications")
@RequiredArgsConstructor
public class AdminApplicationController implements AdminApplicationControllerDocs {

    private final AdminVolunteerHourService adminVolunteerHourService;

    @Override
    @PatchMapping("/{applicationId}/volunteer-hours")
    public ResponseEntity<ApiResult<AdminVolunteerHoursResponseDto>> changeVolunteerHours(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long applicationId,
            @Valid @RequestBody VolunteerHoursUpdateRequestDto request) {
        return ApiResponse.of(SuccessType.SUCCESS,
                adminVolunteerHourService.change(principal.accountId(), applicationId, request));
    }
}
