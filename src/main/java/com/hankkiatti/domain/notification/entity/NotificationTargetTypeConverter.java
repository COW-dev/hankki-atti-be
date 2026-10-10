package com.hankkiatti.domain.notification.entity;

import com.hankkiatti.domain.common.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NotificationTargetTypeConverter extends AbstractEnumConverter<NotificationTargetType> {

    public NotificationTargetTypeConverter() {
        super(NotificationTargetType.class);
    }
}
