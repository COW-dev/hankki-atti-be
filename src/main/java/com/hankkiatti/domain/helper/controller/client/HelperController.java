package com.hankkiatti.domain.helper.controller.client;

import com.hankkiatti.domain.helper.dto.request.HelperSignupRequestDto;
import com.hankkiatti.domain.helper.dto.response.HelperSignupResponseDto;
import com.hankkiatti.domain.helper.service.HelperSignupService;
import com.hankkiatti.global.response.ApiResponse;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.response.type.SuccessType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/helpers")
@RequiredArgsConstructor
public class HelperController implements HelperControllerDocs {

    private final HelperSignupService helperSignupService;

    @Override
    @PostMapping("/signup")
    public ResponseEntity<ApiResult<HelperSignupResponseDto>> signup(@Valid @RequestBody HelperSignupRequestDto request) {
        return ApiResponse.of(SuccessType.CREATED, helperSignupService.signup(request));
    }
}
