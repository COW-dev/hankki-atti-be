package com.hankkiatti.domain.admin.entity;

import com.hankkiatti.domain.common.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AdminGradeConverter extends AbstractEnumConverter<AdminGrade> {

    public AdminGradeConverter() {
        super(AdminGrade.class);
    }
}
