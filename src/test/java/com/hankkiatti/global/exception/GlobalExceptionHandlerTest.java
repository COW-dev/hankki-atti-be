package com.hankkiatti.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hankkiatti.global.response.ApiResponse;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.response.type.ErrorCode;
import com.hankkiatti.global.response.type.SuccessType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(handler)
                .build();
    }

    @Test
    void of_데이터있는성공응답_ApiResult로감싸서반환() throws Exception {
        // when & then
        mockMvc.perform(post("/test/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"한끼\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultType").value("SUCCESS"))
                .andExpect(jsonPath("$.httpStatusCode").value(201))
                .andExpect(jsonPath("$.message").value(SuccessType.CREATED.getMessage()))
                .andExpect(jsonPath("$.data").value("한끼"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void of_데이터없는성공응답_data필드생략() throws Exception {
        // when & then
        mockMvc.perform(get("/test/empty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultType").value("SUCCESS"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void handleDomainException_도메인예외_에러코드상태와메시지반환하고detail은숨김() throws Exception {
        // when & then
        mockMvc.perform(get("/test/domain"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultType").value("FAIL"))
                .andExpect(jsonPath("$.httpStatusCode").value(404))
                .andExpect(jsonPath("$.code").value("TEST_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(TestErrorType.NOT_FOUND.getMessage()))
                .andExpect(content().string(not(containsString("secret-id"))));
    }

    @Test
    void handleDomainException_clientMessage지정_지정한메시지반환() throws Exception {
        // when & then
        mockMvc.perform(get("/test/domain-client-message"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("이미 지난 식사 시간입니다."));
    }

    @Test
    void handleBindingErrors_요청본문검증실패_422와필드메시지반환() throws Exception {
        // when & then
        mockMvc.perform(post("/test/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("name")));
    }

    @Test
    void handleBindingErrors_검증오류5건초과_나머지건수로요약() throws Exception {
        // when & then
        mockMvc.perform(post("/test/many")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value(containsString("외 1건")));
    }

    @Test
    void handleHandlerMethodValidation_파라미터제약위반_422와파라미터메시지반환() throws Exception {
        // when & then
        mockMvc.perform(get("/test/page").param("page", "0"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value(containsString("page")));
    }

    @Test
    void handleNotReadable_JSON형식오류_400반환() throws Exception {
        // when & then
        mockMvc.perform(post("/test/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultType").value("FAIL"));
    }

    @Test
    void handleInvalidParameter_파라미터타입불일치_400반환() throws Exception {
        // when & then
        mockMvc.perform(get("/test/items/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void handleInvalidParameter_필수파라미터누락_400반환() throws Exception {
        // when & then
        mockMvc.perform(get("/test/page"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void handleIllegalArgument_IllegalArgumentException_내부메시지숨기고400반환() throws Exception {
        // when & then
        mockMvc.perform(get("/test/illegal"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("internal-detail"))));
    }

    @Test
    void handleResponseStatus_사유있는409_CONFLICT와사유반환() throws Exception {
        // when & then
        mockMvc.perform(get("/test/status-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 처리된 요청입니다."));
    }

    @Test
    void handleResponseStatus_매핑없는상태코드_500반환() throws Exception {
        // when & then
        mockMvc.perform(get("/test/status-unavailable"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void handleDataIntegrityViolation_무결성위반_409반환() throws Exception {
        // when & then
        mockMvc.perform(get("/test/integrity"))
                .andExpect(status().isConflict());
    }

    @Test
    void handleMethodNotSupported_지원하지않는메서드_405반환() throws Exception {
        // when & then
        mockMvc.perform(delete("/test/items"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void handleException_처리되지않은예외_내부메시지숨기고500반환() throws Exception {
        // when & then
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("internal-detail"))));
    }

    @Test
    void handleNoResourceFound_없는경로_404반환() {
        // when
        ResponseEntity<ApiResult<?>> response = handler.handleNoResourceFound(mock(NoResourceFoundException.class));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void handleNotAcceptable_제공불가미디어타입_406반환() {
        // when
        ResponseEntity<ApiResult<?>> response =
                handler.handleNotAcceptable(new HttpMediaTypeNotAcceptableException("not acceptable"));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void handleConstraintViolation_서비스검증실패_request접두어제거한경로로422반환() {
        // given
        Path path = mock(Path.class);
        given(path.toString()).willReturn("create.request.name");
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        given(violation.getPropertyPath()).willReturn(path);
        given(violation.getMessage()).willReturn("공백일 수 없습니다");

        // when
        ResponseEntity<ApiResult<?>> response =
                handler.handleConstraintViolation(new ConstraintViolationException(Set.of(violation)));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().message()).contains("name: 공백일 수 없습니다");
        assertThat(response.getBody().message()).doesNotContain("create.request");
    }

    @RestController
    static class TestController {

        record ItemRequest(
                @NotBlank
                String name
        ) {}

        record ManyFieldsRequest(
                @NotBlank
                String a,

                @NotBlank
                String b,

                @NotBlank
                String c,

                @NotBlank
                String d,

                @NotBlank
                String e,

                @NotBlank
                String f
        ) {}

        @PostMapping("/test/items")
        ResponseEntity<ApiResult<String>> create(@Valid @RequestBody ItemRequest request) {
            return ApiResponse.of(SuccessType.CREATED, request.name());
        }

        @PostMapping("/test/many")
        ResponseEntity<ApiResult<Void>> many(@Valid @RequestBody ManyFieldsRequest request) {
            return ApiResponse.of(SuccessType.SUCCESS);
        }

        @GetMapping("/test/empty")
        ResponseEntity<ApiResult<Void>> empty() {
            return ApiResponse.of(SuccessType.SUCCESS);
        }

        @GetMapping("/test/items/{id}")
        ResponseEntity<ApiResult<Long>> item(@PathVariable Long id) {
            return ApiResponse.of(SuccessType.SUCCESS, id);
        }

        @GetMapping("/test/page")
        ResponseEntity<ApiResult<Integer>> page(@RequestParam @Min(1) int page) {
            return ApiResponse.of(SuccessType.SUCCESS, page);
        }

        @GetMapping("/test/domain")
        void domain() {
            throw new TestException(TestErrorType.NOT_FOUND, "secret-id=42");
        }

        @GetMapping("/test/domain-client-message")
        void domainWithClientMessage() {
            throw new TestException(TestErrorType.NOT_FOUND, null, "이미 지난 식사 시간입니다.");
        }

        @GetMapping("/test/illegal")
        void illegal() {
            throw new IllegalArgumentException("internal-detail");
        }

        @GetMapping("/test/status-conflict")
        void statusConflict() {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 요청입니다.");
        }

        @GetMapping("/test/status-unavailable")
        void statusUnavailable() {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        }

        @GetMapping("/test/integrity")
        void integrity() {
            throw new DataIntegrityViolationException("duplicate key");
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("internal-detail");
        }
    }

    enum TestErrorType implements ErrorCode {
        NOT_FOUND;

        @Override
        public int getHttpStatusCode() {
            return 404;
        }

        @Override
        public String getMessage() {
            return "대상을 찾을 수 없습니다.";
        }
    }

    static class TestException extends DomainException {

        TestException(ErrorCode errorCode, String detail) {
            super(errorCode, detail);
        }

        TestException(ErrorCode errorCode, String detail, String clientMessage) {
            super(errorCode, detail, clientMessage);
        }
    }
}
