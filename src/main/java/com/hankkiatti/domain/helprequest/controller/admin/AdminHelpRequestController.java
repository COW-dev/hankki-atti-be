package com.hankkiatti.domain.helprequest.controller.admin;

import com.hankkiatti.domain.helprequest.dto.response.AdminHelpRequestRow;
import com.hankkiatti.domain.helprequest.dto.response.AdminHelpRequestSummaryResponseDto;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.service.AdminHelpRequestService;
import com.hankkiatti.global.response.ApiResponse;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.response.type.SuccessType;
import com.hankkiatti.global.security.AuthPrincipal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/help-requests")
@RequiredArgsConstructor
public class AdminHelpRequestController implements AdminHelpRequestControllerDocs {

    private final AdminHelpRequestService adminHelpRequestService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResult<List<AdminHelpRequestRow>>> getHelpRequests(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) HelpRequestStatus status,
            @RequestParam(required = false) String q) {
        return ApiResponse.of(SuccessType.SUCCESS,
                adminHelpRequestService.getHelpRequests(principal.accountId(), from, to, status, q));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<ApiResult<AdminHelpRequestSummaryResponseDto>> getSummary(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.of(SuccessType.SUCCESS, adminHelpRequestService.getSummary(principal.accountId(), from, to));
    }
}
