package com.hankkiatti.domain.notification.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationJobStatus implements LabeledEnum {

    PENDING("처리 대기"),
    PROCESSING("처리 중"),
    DONE("처리 완료"),
    FAILED("처리 실패");

    private final String label;
}
