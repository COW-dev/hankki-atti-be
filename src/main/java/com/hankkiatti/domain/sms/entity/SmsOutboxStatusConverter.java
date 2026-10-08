package com.hankkiatti.domain.sms.entity;

import com.hankkiatti.domain.common.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SmsOutboxStatusConverter extends AbstractEnumConverter<SmsOutboxStatus> {

    public SmsOutboxStatusConverter() {
        super(SmsOutboxStatus.class);
    }
}
