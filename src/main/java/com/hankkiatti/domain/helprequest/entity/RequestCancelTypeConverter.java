package com.hankkiatti.domain.helprequest.entity;

import com.hankkiatti.domain.common.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RequestCancelTypeConverter extends AbstractEnumConverter<RequestCancelType> {

    public RequestCancelTypeConverter() {
        super(RequestCancelType.class);
    }
}
