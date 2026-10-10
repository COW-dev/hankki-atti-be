package com.hankkiatti.domain.notification.entity;

import com.hankkiatti.domain.common.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NotificationJobStatusConverter extends AbstractEnumConverter<NotificationJobStatus> {

    public NotificationJobStatusConverter() {
        super(NotificationJobStatus.class);
    }
}
