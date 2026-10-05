package com.hankkiatti.domain.common;

/**
 * 한글 표시 문구를 가진 enum. 메일·알림처럼 서버가 만드는 문장에서 쓴다.
 */
public interface LabeledEnum {
    String getLabel();
}
