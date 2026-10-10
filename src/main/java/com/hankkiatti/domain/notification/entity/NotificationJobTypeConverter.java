package com.hankkiatti.domain.notification.entity;

import com.hankkiatti.domain.common.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NotificationJobTypeConverter extends AbstractEnumConverter<NotificationJobType> {

    public NotificationJobTypeConverter() {
        super(NotificationJobType.class);
    }
}
