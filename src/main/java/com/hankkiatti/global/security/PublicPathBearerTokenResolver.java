package com.hankkiatti.global.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 로그인·refresh·로그아웃에서는 Authorization 헤더를 무시한다.
 * 만료된 access 토큰이 붙어 와도 이 경로들은 401 없이 동작해야 하기 때문이다.
 */
public class PublicPathBearerTokenResolver implements BearerTokenResolver {

    private final BearerTokenResolver delegate = new DefaultBearerTokenResolver();
    private final RequestMatcher publicPaths;

    public PublicPathBearerTokenResolver(RequestMatcher publicPaths) {
        this.publicPaths = publicPaths;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        if (publicPaths.matches(request)) {
            return null;
        }
        return delegate.resolve(request);
    }
}
