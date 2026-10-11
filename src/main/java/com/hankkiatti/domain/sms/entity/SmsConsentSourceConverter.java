package com.hankkiatti.domain.sms.entity;

import com.hankkiatti.domain.common.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SmsConsentSourceConverter extends AbstractEnumConverter<SmsConsentSource> {

    public SmsConsentSourceConverter() {
        super(SmsConsentSource.class);
    }
}
