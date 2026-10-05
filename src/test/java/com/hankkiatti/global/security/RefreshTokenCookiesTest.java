package com.hankkiatti.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.auth.entity.TokenAudience;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

class RefreshTokenCookiesTest {

    private RefreshTokenCookies cookies(boolean secure) {
        return new RefreshTokenCookies(new AuthProperties(
                new AuthProperties.Jwt(null, "hankki-atti", Duration.ofMinutes(30), Duration.ofDays(14)),
                new AuthProperties.RefreshCookie(secure),
                new AuthProperties.Cors(List.of("http://localhost:3000"))));
    }

    @Test
    void create_Secure설정_Host접두사와보안속성() {
        // when
        ResponseCookie cookie = cookies(true).create(TokenAudience.USER, "token");

        // then
        assertThat(cookie.getName()).isEqualTo("__Host-hankki_rt");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getDomain()).isNull();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(14));
    }

    @Test
    void name_로컬http설정_접두사없고앱별로이름분리() {
        // given
        RefreshTokenCookies local = cookies(false);

        // when & then
        assertThat(local.name(TokenAudience.USER)).isEqualTo("hankki_rt");
        assertThat(local.name(TokenAudience.ADMIN)).isEqualTo("hankki_admin_rt");
    }

    @Test
    void clear_만료쿠키() {
        // when
        ResponseCookie cookie = cookies(true).clear(TokenAudience.ADMIN);

        // then
        assertThat(cookie.getName()).isEqualTo("__Host-hankki_admin_rt");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
    }

    @Test
    void read_해당앱쿠키만읽음() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("__Host-hankki_admin_rt", "admin-token"), new Cookie("__Host-hankki_rt", "user-token"));
        RefreshTokenCookies secure = cookies(true);

        // when & then
        assertThat(secure.read(request, TokenAudience.USER)).contains("user-token");
        assertThat(secure.read(request, TokenAudience.ADMIN)).contains("admin-token");
        assertThat(secure.read(new MockHttpServletRequest(), TokenAudience.USER)).isEmpty();
    }
}
