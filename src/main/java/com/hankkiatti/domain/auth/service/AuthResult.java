package com.hankkiatti.domain.auth.service;

/**
 * 응답 본문과, 쿠키로 내보낼 refresh 토큰 원문.
 */
public record AuthResult<T>(T body, String refreshToken) {}
