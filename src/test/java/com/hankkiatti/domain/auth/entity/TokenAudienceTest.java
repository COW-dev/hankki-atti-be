package com.hankkiatti.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.account.entity.AccountRole;
import org.junit.jupiter.api.Test;

class TokenAudienceTest {

    @Test
    void allows_사용자앱토큰_장애학생과도우미만허용() {
        // when & then
        assertThat(TokenAudience.USER.allows(AccountRole.STUDENT)).isTrue();
        assertThat(TokenAudience.USER.allows(AccountRole.HELPER)).isTrue();
        assertThat(TokenAudience.USER.allows(AccountRole.ADMIN)).isFalse();
    }

    @Test
    void allows_관리자토큰_관리자만허용() {
        // when & then
        assertThat(TokenAudience.ADMIN.allows(AccountRole.ADMIN)).isTrue();
        assertThat(TokenAudience.ADMIN.allows(AccountRole.STUDENT)).isFalse();
    }

    @Test
    void fromClaim_모르는값_null() {
        // when & then
        assertThat(TokenAudience.fromClaim("admin")).isEqualTo(TokenAudience.ADMIN);
        assertThat(TokenAudience.fromClaim("ADMIN")).isNull();
    }
}
