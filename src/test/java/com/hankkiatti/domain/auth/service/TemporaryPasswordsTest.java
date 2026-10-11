package com.hankkiatti.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class TemporaryPasswordsTest {

    @RepeatedTest(50)
    void generate_12자_영문숫자특수문자를모두포함하고헷갈리는글자는없다() {
        // when
        String password = TemporaryPasswords.generate();

        // then
        assertThat(password).hasSize(TemporaryPasswords.LENGTH);
        assertThat(password).matches("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*]).*$");
        assertThat(password).doesNotContainAnyWhitespaces();
        assertThat(password).doesNotContain("0", "O", "1", "l", "I");
    }

    @Test
    void generate_부를때마다다른값() {
        // when
        Set<String> passwords = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            passwords.add(TemporaryPasswords.generate());
        }

        // then
        assertThat(passwords).hasSize(100);
    }
}
