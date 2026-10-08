package com.hankkiatti.domain.helprequest.controller.client;

import com.hankkiatti.domain.helprequest.dto.request.HelpRequestCreateRequestDto;
import com.hankkiatti.domain.helprequest.dto.response.HelpRequestCreateResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.MyHelpRequestResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.MyHelpRequestsResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.TimeOptionDateResponseDto;
import com.hankkiatti.domain.helprequest.service.HelpRequestService;
import com.hankkiatti.domain.helprequest.service.TimeOptionService;
import com.hankkiatti.global.response.ApiResponse;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.response.type.SuccessType;
import com.hankkiatti.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/help-requests")
@RequiredArgsConstructor
public class HelpRequestController implements HelpRequestControllerDocs {

    private final TimeOptionService timeOptionService;
    private final HelpRequestService helpRequestService;

    @Override
    @GetMapping("/time-options")
    public ResponseEntity<ApiResult<List<TimeOptionDateResponseDto>>> getTimeOptions() {
        return ApiResponse.of(SuccessType.SUCCESS, timeOptionService.getTimeOptions());
    }

    @Override
    @GetMapping("/me")
    public ResponseEntity<ApiResult<MyHelpRequestsResponseDto>> getMyRequests(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.of(SuccessType.SUCCESS, helpRequestService.getMyRequests(principal.accountId()));
    }

    @Override
    @PostMapping
    public ResponseEntity<ApiResult<HelpRequestCreateResponseDto>> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody HelpRequestCreateRequestDto request) {
        return ApiResponse.of(SuccessType.CREATED, helpRequestService.create(principal.accountId(), request));
    }

    @Override
    @PostMapping("/{helpRequestId}/withdraw")
    public ResponseEntity<ApiResult<MyHelpRequestResponseDto>> withdraw(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long helpRequestId) {
        return ApiResponse.of(SuccessType.SUCCESS, helpRequestService.withdraw(principal.accountId(), helpRequestId));
    }
}
