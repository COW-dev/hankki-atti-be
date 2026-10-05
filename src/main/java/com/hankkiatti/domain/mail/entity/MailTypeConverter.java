package com.hankkiatti.domain.mail.entity;

import com.hankkiatti.domain.common.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MailTypeConverter extends AbstractEnumConverter<MailType> {

    public MailTypeConverter() {
        super(MailType.class);
    }
}
