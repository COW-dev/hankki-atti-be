package com.hankkiatti.domain.sms.entity;

import com.hankkiatti.domain.common.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SmsTypeConverter extends AbstractEnumConverter<SmsType> {

    public SmsTypeConverter() {
        super(SmsType.class);
    }
}
