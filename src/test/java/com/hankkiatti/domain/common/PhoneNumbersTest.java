package com.hankkiatti.domain.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PhoneNumbersTest {

    @ParameterizedTest
    @CsvSource({
            "01012345678, 010-1234-5678",
            "010-1234-5678, 010-1234-5678",
            "0111234567, 011-123-4567",
            "011-123-4567, 011-123-4567"
    })
    void normalize_하이픈있든없든_하이픈형식(String raw, String expected) {
        // when & then
        assertThat(PhoneNumbers.normalize(raw)).isEqualTo(expected);
    }
}
