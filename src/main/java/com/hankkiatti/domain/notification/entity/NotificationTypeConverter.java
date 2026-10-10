package com.hankkiatti.domain.notification.entity;

import com.hankkiatti.domain.common.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NotificationTypeConverter extends AbstractEnumConverter<NotificationType> {

    public NotificationTypeConverter() {
        super(NotificationType.class);
    }
}
