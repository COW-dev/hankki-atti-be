package com.hankkiatti.global.security;

public record AccessToken(String value, long expiresInSeconds) {}
