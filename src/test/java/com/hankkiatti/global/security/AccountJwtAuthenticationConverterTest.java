package com.hankkiatti.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.auth.entity.TokenAudience;
import com.hankkiatti.support.TestAccounts;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

@ExtendWith(MockitoExtension.class)
class AccountJwtAuthenticationConverterTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountJwtAuthenticationConverter converter;

    private Jwt jwt(String subject, String audience, Object version) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject)
                .audience(List.of(audience))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (version != null) {
            builder.claim(JwtTokenProvider.VERSION_CLAIM, version);
        }
        return builder.build();
    }

    private List<String> authorities(AbstractAuthenticationToken token) {
        return token.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void convert_정상계정_역할권한과주체설정() {
        // given
        Account helper = TestAccounts.withId(2L, AccountRole.HELPER, "hash", false);
        given(accountRepository.findById(2L)).willReturn(Optional.of(helper));

        // when
        AbstractAuthenticationToken token = converter.convert(jwt("2", "user", 0));

        // then
        assertThat(authorities(token)).containsExactly("ROLE_HELPER");
        assertThat(token.getPrincipal()).isEqualTo(new AuthPrincipal(2L, AccountRole.HELPER, TokenAudience.USER));
    }

    @Test
    void convert_비밀번호변경필요계정_변경전용권한만부여() {
        // given
        given(accountRepository.findById(1L))
                .willReturn(Optional.of(TestAccounts.withId(1L, AccountRole.STUDENT, "hash", true)));

        // when
        AbstractAuthenticationToken token = converter.convert(jwt("1", "user", 0));

        // then
        assertThat(authorities(token)).containsExactly(AuthAuthorities.PASSWORD_CHANGE_ONLY);
    }

    @Test
    void convert_관리자는변경필요상태여도_관리자권한() {
        // given
        given(accountRepository.findById(3L))
                .willReturn(Optional.of(TestAccounts.withId(3L, AccountRole.ADMIN, "hash", true)));

        // when
        AbstractAuthenticationToken token = converter.convert(jwt("3", "admin", 0));

        // then
        assertThat(authorities(token)).containsExactly("ROLE_ADMIN");
    }

    @Test
    void convert_토큰버전이다름_무효() {
        // given
        given(accountRepository.findById(1L))
                .willReturn(Optional.of(TestAccounts.withId(1L, AccountRole.STUDENT, "hash", false)));

        // when & then
        assertThatThrownBy(() -> converter.convert(jwt("1", "user", 1)))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void convert_비활성계정_무효() {
        // given
        Account student = TestAccounts.withId(1L, AccountRole.STUDENT, "hash", false);
        student.deactivate(LocalDateTime.of(2026, 10, 5, 12, 0));
        given(accountRepository.findById(1L)).willReturn(Optional.of(student));

        // when & then
        assertThatThrownBy(() -> converter.convert(jwt("1", "user", student.getTokenVersion())))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void convert_학생계정이관리자토큰사용_무효() {
        // given
        given(accountRepository.findById(1L))
                .willReturn(Optional.of(TestAccounts.withId(1L, AccountRole.STUDENT, "hash", false)));

        // when & then
        assertThatThrownBy(() -> converter.convert(jwt("1", "admin", 0)))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void convert_없는계정_무효() {
        // given
        given(accountRepository.findById(9L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> converter.convert(jwt("9", "user", 0)))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void convert_필수클레임누락이나형식오류_무효() {
        // when & then
        assertThatThrownBy(() -> converter.convert(jwt("abc", "user", 0)))
                .isInstanceOf(InvalidBearerTokenException.class);
        assertThatThrownBy(() -> converter.convert(jwt("1", "unknown", 0)))
                .isInstanceOf(InvalidBearerTokenException.class);
        assertThatThrownBy(() -> converter.convert(jwt("1", "user", null)))
                .isInstanceOf(InvalidBearerTokenException.class);
    }
}
