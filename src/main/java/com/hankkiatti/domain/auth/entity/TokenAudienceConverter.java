package com.hankkiatti.domain.auth.entity;

import com.hankkiatti.domain.common.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TokenAudienceConverter extends AbstractEnumConverter<TokenAudience> {

    public TokenAudienceConverter() {
        super(TokenAudience.class);
    }
}
