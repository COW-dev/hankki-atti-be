package com.hankkiatti.domain.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.account.dto.request.MeContactUpdateRequestDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@code @PhoneNumber}·{@code @KakaoId} 규칙. 도우미 가입·연락처 수정이 같은 규칙을 쓴다.
 */
class ContactValidationTest {

    private static final String VALID_PHONE = "010-1234-5678";
    private static final String VALID_KAKAO_ID = "hankki_helper";

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<ConstraintViolation<MeContactUpdateRequestDto>> validate(String phone, String kakaoId) {
        return validator.validate(new MeContactUpdateRequestDto(phone, kakaoId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"010-1234-5678", "01012345678", "011-123-4567", "019-1234-5678"})
    void 휴대전화형식_통과(String phone) {
        // when & then
        assertThat(validate(phone, VALID_KAKAO_ID)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"02-123-4567", "012-1234-5678", "010-12-5678", "010 1234 5678", "010-1234-567a"})
    void 휴대전화형식아님_실패(String phone) {
        // when
        Set<ConstraintViolation<MeContactUpdateRequestDto>> violations = validate(phone, VALID_KAKAO_ID);

        // then
        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .containsExactly("휴대전화 번호 형식이 아닙니다.");
    }

    @Test
    void 카톡ID_공백없이50자까지_통과() {
        // when & then
        assertThat(validate(VALID_PHONE, "a".repeat(50))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"hankki helper", " hankki", "hankki\t"})
    void 카톡ID_공백포함_메시지하나로실패(String kakaoId) {
        // when
        Set<ConstraintViolation<MeContactUpdateRequestDto>> violations = validate(VALID_PHONE, kakaoId);

        // then
        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .containsExactly("카톡 ID는 공백 없이 50자 이하로 입력해 주세요.");
    }

    @Test
    void 카톡ID_51자_실패() {
        // when & then
        assertThat(validate(VALID_PHONE, "a".repeat(51))).hasSize(1);
    }
}
