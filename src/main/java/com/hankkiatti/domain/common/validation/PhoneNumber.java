package com.hankkiatti.domain.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 휴대전화 번호 형식: 010·011·016~019로 시작, 하이픈은 있어도 없어도 된다. 저장할 때는 {@code PhoneNumbers.normalize}로 맞춘다.
 * 도우미 가입·연락처 수정, 장애학생 등록에서 같이 쓴다. null은 통과시키므로 필수 여부는 {@code @NotBlank}를 함께 붙인다.
 */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ReportAsSingleViolation
@Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$")
public @interface PhoneNumber {

    String message() default "휴대전화 번호 형식이 아닙니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
