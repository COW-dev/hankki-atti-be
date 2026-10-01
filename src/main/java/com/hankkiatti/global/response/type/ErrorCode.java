package com.hankkiatti.global.response.type;

public interface ErrorCode {
    int getHttpStatusCode();
    String getMessage();
}
