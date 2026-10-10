package com.hankkiatti.domain.notification.entity;

import com.hankkiatti.domain.common.LabeledEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 알림을 누르면 이동할 대상.
 */
@Getter
@RequiredArgsConstructor
public enum NotificationTargetType implements LabeledEnum {

    HELP_REQUEST("신청"),
    APPLICATION("지원"),
    NOTICE("공지");

    private final String label;
}
