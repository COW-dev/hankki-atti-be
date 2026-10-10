package com.hankkiatti.domain.helper.controller.admin;

import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.helper.dto.response.AdminHelperActivityResponseDto;
import com.hankkiatti.domain.helper.dto.response.AdminHelperDetailResponseDto;
import com.hankkiatti.domain.helper.dto.response.AdminHelpersResponseDto;
import com.hankkiatti.domain.helper.service.AdminHelperService;
import com.hankkiatti.global.response.ApiResponse;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.response.type.SuccessType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/helpers")
@RequiredArgsConstructor
public class AdminHelperController implements AdminHelperControllerDocs {

    private final AdminHelperService adminHelperService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResult<AdminHelpersResponseDto>> getHelpers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) Boolean attiMember,
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.of(SuccessType.SUCCESS, adminHelperService.getHelpers(q, status, attiMember, page));
    }

    @Override
    @GetMapping("/{helperId}")
    public ResponseEntity<ApiResult<AdminHelperDetailResponseDto>> getHelper(@PathVariable Long helperId) {
        return ApiResponse.of(SuccessType.SUCCESS, adminHelperService.getHelper(helperId));
    }

    @Override
    @GetMapping("/{helperId}/activities")
    public ResponseEntity<ApiResult<List<AdminHelperActivityResponseDto>>> getActivities(@PathVariable Long helperId) {
        return ApiResponse.of(SuccessType.SUCCESS, adminHelperService.getActivities(helperId));
    }
}
