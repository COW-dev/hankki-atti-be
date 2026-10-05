package com.hankkiatti.global.response;

import com.hankkiatti.global.response.type.SuccessType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

public final class ApiResponse {

    private ApiResponse() {}

    public static <T> ResponseEntity<ApiResult<T>> of(SuccessType type, T data) {
        return ResponseEntity
                .status(type.getHttpStatusCode())
                .body(ApiResult.success(type, data));
    }

    // 쿠키 등 응답 헤더가 필요할 때 쓴다
    public static <T> ResponseEntity<ApiResult<T>> of(SuccessType type, T data, HttpHeaders headers) {
        return ResponseEntity
                .status(type.getHttpStatusCode())
                .headers(headers)
                .body(ApiResult.success(type, data));
    }

    public static ResponseEntity<ApiResult<Void>> of(SuccessType type) {
        return ResponseEntity
                .status(type.getHttpStatusCode())
                .body(ApiResult.<Void>success(type, null));
    }
}
