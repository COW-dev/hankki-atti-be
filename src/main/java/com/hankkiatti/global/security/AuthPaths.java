package com.hankkiatti.global.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 인증 경로 모음. 공개 경로는 SecurityConfig의 permitAll과 Bearer 헤더 무시에 같이 쓴다.
 */
public final class AuthPaths {

    public static final String USER_LOGIN = "/api/auth/login";
    public static final String USER_REFRESH = "/api/auth/refresh";
    public static final String USER_LOGOUT = "/api/auth/logout";
    public static final String USER_PASSWORD = "/api/auth/password";
    public static final String ADMIN_LOGIN = "/api/admin/auth/login";
    public static final String ADMIN_REFRESH = "/api/admin/auth/refresh";
    public static final String ADMIN_LOGOUT = "/api/admin/auth/logout";
    // 도우미 회원가입 — 로그인 전에 부르는 API라 공개
    public static final String HELPER_SIGNUP = "/api/helpers/signup";

    private AuthPaths() {}

    public static RequestMatcher publicAuthEndpoints() {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        return new OrRequestMatcher(
                paths.matcher(HttpMethod.POST, USER_LOGIN),
                paths.matcher(HttpMethod.POST, USER_REFRESH),
                paths.matcher(HttpMethod.POST, USER_LOGOUT),
                paths.matcher(HttpMethod.POST, ADMIN_LOGIN),
                paths.matcher(HttpMethod.POST, ADMIN_REFRESH),
                paths.matcher(HttpMethod.POST, ADMIN_LOGOUT),
                paths.matcher(HttpMethod.POST, HELPER_SIGNUP)
        );
    }
}
