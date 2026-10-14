package com.hankkiatti.global.exception;

import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.response.type.CommonErrorType;
import com.hankkiatti.global.response.type.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final int MAX_ERROR_DETAILS = 5;

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResult<?>> handleBindingErrors(BindException exception) {
        String detail = buildFieldErrorDetail(exception.getBindingResult().getFieldErrors());
        String message = buildValidationMessage(detail);
        log.debug("유효성 검사 오류: {}", detail);

        return json(CommonErrorType.VALIDATION_FAILED, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResult<?>> handleConstraintViolation(ConstraintViolationException exception) {
        List<String> details = exception.getConstraintViolations().stream()
                .map(v -> normalizeConstraintPath(v.getPropertyPath().toString()) + ": " + v.getMessage())
                .toList();
        String detail = summarizeDetails(details);
        String message = buildValidationMessage(detail);
        log.debug("제약 조건 위반: {}", detail);

        return json(CommonErrorType.VALIDATION_FAILED, message);
    }

    // 컨트롤러 메서드 파라미터(@RequestParam, @PathVariable 등)에 붙은 제약 조건 위반
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResult<?>> handleHandlerMethodValidation(HandlerMethodValidationException exception) {
        List<String> details = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> result.getMethodParameter().getParameterName() + ": " + error.getDefaultMessage()))
                .toList();
        String detail = summarizeDetails(details);
        String message = buildValidationMessage(detail);
        log.debug("파라미터 검증 오류: {}", detail);

        return json(CommonErrorType.VALIDATION_FAILED, message);
    }

    // Jackson 메시지에는 입력값 일부가 들어가므로(예: Unrecognized token '...') 어느 필드인지만 남긴다
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResult<?>> handleNotReadable(HttpMessageNotReadableException exception) {
        JacksonException cause = findCause(exception, JacksonException.class);
        log.warn("요청 본문을 읽을 수 없습니다: type={}, path={}",
                cause == null ? exception.getClass().getSimpleName() : cause.getClass().getSimpleName(),
                cause == null ? null : cause.getPathReference());
        return json(CommonErrorType.INVALID_REQUEST,
                "요청 값 검증에 실패했습니다. (요청 본문 형식이 올바르지 않습니다)");
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResult<?>> handleInvalidParameter(Exception exception) {
        log.debug("요청 파라미터 오류: {}", exception.getMessage());
        return json(CommonErrorType.INVALID_REQUEST, CommonErrorType.INVALID_REQUEST.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResult<?>> handleResponseStatus(ResponseStatusException exception) {
        CommonErrorType errorType = mapStatusToErrorType(exception.getStatusCode());
        String message = defaultIfBlank(exception.getReason(), errorType.getMessage());
        log.warn("HTTP 상태 예외: 상태={}, 사유={}", exception.getStatusCode(), exception.getReason(), exception);

        return json(errorType, message);
    }

    // DB 메시지에는 위반한 값이 그대로 들어가므로(예: Duplicate entry 'x@mju.ac.kr') 어느 제약인지만 남긴다.
    // NOT NULL·CHECK 위반은 요청과 상태가 충돌한 게 아니라 서버가 값을 잘못 채운 것이라 500으로 둔다
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResult<?>> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        org.hibernate.exception.ConstraintViolationException violation =
                findCause(exception, org.hibernate.exception.ConstraintViolationException.class);
        SQLException sqlException = findCause(exception, SQLException.class);
        ConstraintKind kind = violation == null ? null : violation.getKind();
        String constraint = violation == null ? null : violation.getConstraintName();
        String sqlState = sqlException == null ? null : sqlException.getSQLState();
        Integer errorCode = sqlException == null ? null : sqlException.getErrorCode();
        if (kind == ConstraintKind.NOT_NULL || kind == ConstraintKind.CHECK) {
            log.error("데이터 무결성 위반(서버 값 오류): constraint={}, kind={}, sqlState={}, errorCode={}",
                    constraint, kind, sqlState, errorCode);
            return json(CommonErrorType.INTERNAL_ERROR, CommonErrorType.INTERNAL_ERROR.getMessage());
        }
        log.warn("데이터 무결성 위반: constraint={}, kind={}, sqlState={}, errorCode={}",
                constraint, kind, sqlState, errorCode);
        return json(CommonErrorType.CONFLICT, CommonErrorType.CONFLICT.getMessage());
    }

    // 락 대기 시간 초과·교착으로 롤백된 요청. 다시 보내면 대개 성공하므로 전용 코드로 재시도를 안내한다
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiResult<?>> handleLockFailure(PessimisticLockingFailureException exception) {
        log.warn("락 획득 실패: exception={}", exception.getClass().getSimpleName());
        return json(CommonErrorType.LOCK_CONFLICT, CommonErrorType.LOCK_CONFLICT.getMessage());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResult<?>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        log.debug("지원하지 않는 Content-Type: {}", exception.getContentType());
        return json(CommonErrorType.UNSUPPORTED_MEDIA_TYPE, CommonErrorType.UNSUPPORTED_MEDIA_TYPE.getMessage());
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResult<?>> handleNotAcceptable(HttpMediaTypeNotAcceptableException exception) {
        log.warn("지원하지 않는 미디어 타입 요청", exception);
        return json(CommonErrorType.NOT_ACCEPTABLE, CommonErrorType.NOT_ACCEPTABLE.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResult<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        log.warn("지원하지 않는 HTTP 메서드: {}", exception.getMessage(), exception);
        return json(CommonErrorType.METHOD_NOT_ALLOWED, CommonErrorType.METHOD_NOT_ALLOWED.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResult<?>> handleNoResourceFound(NoResourceFoundException exception) {
        log.debug("리소스를 찾을 수 없음: {}", exception.getMessage());
        return json(CommonErrorType.NOT_FOUND, CommonErrorType.NOT_FOUND.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResult<?>> handleDomainException(DomainException exception) {
        // detail은 내부 식별자를 담을 수 있으므로 로그에만 남기고 응답에는 노출하지 않는다.
        log.info(
                "도메인 예외: [{}] {}{}",
                exception.getErrorCode(),
                exception.getMessage(),
                exception.getDetail() == null ? "" : " detail=" + exception.getDetail()
        );
        return json(exception.getErrorCode(), exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<?>> handleException(Exception exception) {
        log.error("처리되지 않은 예외", exception);
        return json(CommonErrorType.INTERNAL_ERROR, CommonErrorType.INTERNAL_ERROR.getMessage());
    }

    private ResponseEntity<ApiResult<?>> json(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.getHttpStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResult.error(errorCode, message));
    }

    private static <T extends Throwable> T findCause(Throwable exception, Class<T> type) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return type.cast(current);
        }
        return null;
    }

    private String buildFieldErrorDetail(List<FieldError> fieldErrors) {
        List<String> details = fieldErrors.stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        return summarizeDetails(details);
    }

    private String summarizeDetails(List<String> details) {
        if (details.isEmpty()) return "";

        int limit = Math.min(details.size(), MAX_ERROR_DETAILS);
        String joined = String.join(", ", details.subList(0, limit));
        int remaining = details.size() - limit;

        if (remaining > 0) joined = joined + ", 외 " + remaining + "건";
        return joined;
    }

    private String buildValidationMessage(String detail) {
        if (detail == null || detail.isBlank()) return "요청 값 검증에 실패했습니다.";
        return "요청 값 검증에 실패했습니다. (" + detail + ")";
    }

    private String defaultIfBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private String normalizeConstraintPath(String path) {
        int requestIndex = path.indexOf("request.");
        if (requestIndex >= 0) return path.substring(requestIndex + "request.".length());
        return path;
    }

    private CommonErrorType mapStatusToErrorType(HttpStatusCode statusCode) {
        if (statusCode == null) return CommonErrorType.INTERNAL_ERROR;
        return switch (statusCode.value()) {
            case 400 -> CommonErrorType.INVALID_REQUEST;
            case 401 -> CommonErrorType.UNAUTHORIZED;
            case 403 -> CommonErrorType.FORBIDDEN;
            case 404 -> CommonErrorType.NOT_FOUND;
            case 409 -> CommonErrorType.CONFLICT;
            case 410 -> CommonErrorType.GONE;
            case 422 -> CommonErrorType.VALIDATION_FAILED;
            case 429 -> CommonErrorType.TOO_MANY_REQUESTS;
            default -> CommonErrorType.INTERNAL_ERROR;
        };
    }
}
