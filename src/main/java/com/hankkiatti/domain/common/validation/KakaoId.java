package com.hankkiatti.domain.common.validation;

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
 * 카카오톡 ID 규칙: 공백 없이 50자 이하. 도우미 가입·연락처 수정, 장애학생 등록에서 같이 쓴다.
 * null은 통과시키므로 필수 여부는 {@code @NotBlank}를 함께 붙인다.
 */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ReportAsSingleViolation
@Size(max = 50)
@Pattern(regexp = "^\\S+$")
public @interface KakaoId {

    String message() default "카톡 ID는 공백 없이 50자 이하로 입력해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
