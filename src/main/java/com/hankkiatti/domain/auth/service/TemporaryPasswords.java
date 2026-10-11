package com.hankkiatti.domain.auth.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 관리자가 발급하는 임시 비밀번호 (장애학생 계정정보 메일·도우미 임시 비밀번호). 받은 사람은 다음 로그인 때 바꿔야 한다.
 * 12자, 영문·숫자·특수문자를 하나 이상 넣어 비밀번호 규칙(@Password)을 지킨다. 헷갈리는 글자(0·O·1·l·I)는 뺀다.
 */
public final class TemporaryPasswords {

    static final int LENGTH = 12;
    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIALS = "!@#$%^&*";
    private static final String ALL = LETTERS + DIGITS + SPECIALS;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TemporaryPasswords() {}

    public static String generate() {
        List<Character> characters = new ArrayList<>();
        characters.add(randomChar(LETTERS));
        characters.add(randomChar(DIGITS));
        characters.add(randomChar(SPECIALS));
        while (characters.size() < LENGTH) {
            characters.add(randomChar(ALL));
        }
        Collections.shuffle(characters, RANDOM);

        StringBuilder password = new StringBuilder(LENGTH);
        characters.forEach(password::append);
        return password.toString();
    }

    private static char randomChar(String candidates) {
        return candidates.charAt(RANDOM.nextInt(candidates.length()));
    }
}
