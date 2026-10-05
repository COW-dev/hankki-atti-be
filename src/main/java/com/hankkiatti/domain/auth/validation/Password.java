package com.hankkiatti.domain.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 비밀번호 규칙: 8~64자, 영문·숫자·특수문자 각 1개 이상. 비밀번호 변경·가입·재설정에서 같이 쓴다.
 * null은 통과시키므로 필수 여부는 {@code @NotBlank}를 함께 붙인다.
 */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ReportAsSingleViolation
@Size(min = 8, max = 64)
@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s]).*$")
public @interface Password {

    String message() default "비밀번호는 8~64자이며 영문·숫자·특수문자를 각각 1개 이상 포함해야 합니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
