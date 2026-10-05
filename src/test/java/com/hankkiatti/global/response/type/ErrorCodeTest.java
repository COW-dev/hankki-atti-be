package com.hankkiatti.global.response.type;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.account.exception.AccountErrorType;
import com.hankkiatti.domain.application.exception.ApplicationErrorType;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.helprequest.exception.HelpRequestErrorType;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ErrorCodeTest {

    @Test
    void getCode_타입이름과상수이름으로생성() {
        // when & then
        assertThat(AuthErrorType.PASSWORD_CHANGE_REQUIRED.getCode()).isEqualTo("AUTH_PASSWORD_CHANGE_REQUIRED");
        assertThat(HelpRequestErrorType.INVALID_STATUS.getCode()).isEqualTo("HELP_REQUEST_INVALID_STATUS");
        assertThat(CommonErrorType.NOT_FOUND.getCode()).isEqualTo("COMMON_NOT_FOUND");
    }

    @Test
    void getCode_같은상수이름이여러타입에있어도_코드는겹치지않음() {
        // given
        List<ErrorCode> all = Stream.of(
                        CommonErrorType.values(),
                        AuthErrorType.values(),
                        AccountErrorType.values(),
                        HelpRequestErrorType.values(),
                        ApplicationErrorType.values())
                .flatMap(Stream::of)
                .map(ErrorCode.class::cast)
                .toList();

        // when
        List<String> codes = all.stream().map(ErrorCode::getCode).toList();

        // then
        assertThat(codes).doesNotHaveDuplicates();
        assertThat(codes).contains("COMMON_NOT_FOUND", "ACCOUNT_NOT_FOUND");
    }
}
