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

    @ParameterizedTest
    @CsvSource({
            "010-1234-5678, +821012345678",
            "01012345678, +821012345678",
            "011-123-4567, +82111234567"
    })
    void toE164_앞의0을빼고국가번호를붙임(String raw, String expected) {
        // when & then
        assertThat(PhoneNumbers.toE164(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "010-1234-5678, 010-****-5678",
            "011-123-4567, 011-***-4567",
            "01012345678, ***********"
    })
    void mask_가운데자리를가림_형식이다르면전부가림(String stored, String expected) {
        // when & then
        assertThat(PhoneNumbers.mask(stored)).isEqualTo(expected);
    }
}
