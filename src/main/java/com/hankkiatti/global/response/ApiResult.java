package com.hankkiatti.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.hankkiatti.global.response.type.ErrorCode;
import com.hankkiatti.global.response.type.ResultType;
import com.hankkiatti.global.response.type.SuccessType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "응답 객체")
@JsonPropertyOrder({"resultType", "httpStatusCode", "code", "message", "data"})
public record ApiResult<T>(

        @Schema(description = "응답 타입", example = "SUCCESS")
        ResultType resultType,

        @Schema(description = "HTTP 상태 코드", example = "200")
        int httpStatusCode,

        // 실패 응답에만 있다. 같은 HTTP 상태(예: 403)의 여러 오류를 프론트가 구분하는 데 쓴다
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "오류 코드 (실패 응답에만)", example = "AUTH_PASSWORD_CHANGE_REQUIRED")
        String code,

        @Schema(description = "응답 메시지", example = "요청에 성공하였습니다.")
        String message,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        T data
) {

    public static <T> ApiResult<T> success(SuccessType successType, T data) {
        return ApiResult.<T>builder()
                .resultType(ResultType.SUCCESS)
                .httpStatusCode(successType.getHttpStatusCode())
                .message(successType.getMessage())
                .data(data)
                .build();
    }

    public static <T> ApiResult<T> error(ErrorCode errorCode, String message, T data) {
        return ApiResult.<T>builder()
                .resultType(ResultType.FAIL)
                .httpStatusCode(errorCode.getHttpStatusCode())
                .code(errorCode.getCode())
                .message(message)
                .data(data)
                .build();
    }

    public static ApiResult<?> error(ErrorCode errorCode, String message) {
        return error(errorCode, message, null);
    }
}
