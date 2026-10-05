package com.hankkiatti.domain.auth.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.auth.dto.request.PasswordChangeRequestDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<ConstraintViolation<PasswordChangeRequestDto>> validate(String newPassword) {
        return validator.validate(new PasswordChangeRequestDto("current", newPassword));
    }

    @ParameterizedTest
    @ValueSource(strings = {"hankki!2026", "Abcdef1!", "한끼아띠abc1@"})
    void 규칙충족_통과(String password) {
        // when & then
        assertThat(validate(password)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc1!", "hankki2026", "hankki!!!!", "12345678!", "        "})
    void 규칙위반_메시지하나로실패(String password) {
        // when
        Set<ConstraintViolation<PasswordChangeRequestDto>> violations = validate(password);

        // then
        assertThat(violations).isNotEmpty();
        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .anyMatch(message -> message.contains("8~64자"));
    }

    @ParameterizedTest
    @ValueSource(ints = {64, 65})
    void 길이경계_64자까지허용(int length) {
        // given
        String password = "a1!" + "x".repeat(length - 3);

        // when & then
        assertThat(validate(password).isEmpty()).isEqualTo(length <= 64);
    }
}
