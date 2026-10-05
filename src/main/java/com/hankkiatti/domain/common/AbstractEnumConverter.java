package com.hankkiatti.domain.common;

import com.hankkiatti.domain.common.exception.EnumConversionException;
import jakarta.persistence.AttributeConverter;

/**
 * enum을 name() 문자열로 VARCHAR 컬럼에 저장한다.
 * enum마다 {@code @Converter(autoApply = true)} 하위 클래스를 하나 두면 엔티티 필드에는 어노테이션이 필요 없다.
 */
public abstract class AbstractEnumConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> enumType;

    protected AbstractEnumConverter(Class<E> enumType) {
        this.enumType = enumType;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        for (E constant : enumType.getEnumConstants()) {
            if (constant.name().equals(dbData)) {
                return constant;
            }
        }
        // 값 자체는 장애 유형 같은 민감정보일 수 있어 detail(로그)에 남기지 않는다
        throw new EnumConversionException("enum=" + enumType.getSimpleName());
    }
}
