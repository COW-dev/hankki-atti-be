package com.hankkiatti.domain.helprequest.controller.client;

import com.hankkiatti.domain.helprequest.dto.response.TimeOptionDateResponseDto;
import com.hankkiatti.domain.helprequest.service.TimeOptionService;
import com.hankkiatti.global.response.ApiResponse;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.response.type.SuccessType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/help-requests")
@RequiredArgsConstructor
public class HelpRequestController implements HelpRequestControllerDocs {

    private final TimeOptionService timeOptionService;

    @Override
    @GetMapping("/time-options")
    public ResponseEntity<ApiResult<List<TimeOptionDateResponseDto>>> getTimeOptions() {
        return ApiResponse.of(SuccessType.SUCCESS, timeOptionService.getTimeOptions());
    }
}
