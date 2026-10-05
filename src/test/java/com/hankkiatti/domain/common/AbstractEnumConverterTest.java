package com.hankkiatti.domain.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hankkiatti.domain.common.exception.EnumConversionException;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.entity.HelpTypeConverter;
import com.hankkiatti.global.response.type.CommonErrorType;
import org.junit.jupiter.api.Test;

class AbstractEnumConverterTest {

    private final HelpTypeConverter converter = new HelpTypeConverter();

    @Test
    void convertToDatabaseColumn_enum_이름문자열로저장() {
        // when
        String dbData = converter.convertToDatabaseColumn(HelpType.SERVING);

        // then
        assertThat(dbData).isEqualTo("SERVING");
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_저장된문자열_enum으로복원() {
        // when
        HelpType helpType = converter.convertToEntityAttribute("MOVING");

        // then
        assertThat(helpType).isEqualTo(HelpType.MOVING);
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_모르는값_예외() {
        // when & then
        assertThatThrownBy(() -> converter.convertToEntityAttribute("UNKNOWN"))
                .isInstanceOf(EnumConversionException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorType.INTERNAL_ERROR);
    }
}
