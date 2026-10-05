package com.hankkiatti.global.security;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.auth.entity.TokenAudience;

/**
 * 인증된 요청의 사용자. 컨트롤러에서 {@code @AuthenticationPrincipal AuthPrincipal principal}로 받는다.
 */
public record AuthPrincipal(Long accountId, AccountRole role, TokenAudience audience) {}
