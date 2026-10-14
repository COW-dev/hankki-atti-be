package com.hankkiatti.domain.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PersonalInfoMasksTest {

    @ParameterizedTest
    @CsvSource({
            "student@mju.ac.kr, st*****@mju.ac.kr",
            "ab@mju.ac.kr, ab***@mju.ac.kr",
            "no-at-sign, no********"
    })
    void email_앞두글자와도메인만보임(String email, String expected) {
        // when & then
        assertThat(PersonalInfoMasks.email(email)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "hankki_student, ha************",
            "abc, ab***",
            "a, a***"
    })
    void identifier_앞두글자만보이고짧아도세글자이상가림(String value, String expected) {
        // when & then
        assertThat(PersonalInfoMasks.identifier(value)).isEqualTo(expected);
    }
}
