package com.hankkiatti.domain.auth.service;

/**
 * 발급한 토큰 한 쌍. refreshToken은 원문이라 쿠키로만 내보내고 로그·응답 본문에 넣지 않는다.
 */
public record IssuedTokens(String accessToken, long expiresIn, String refreshToken) {}
