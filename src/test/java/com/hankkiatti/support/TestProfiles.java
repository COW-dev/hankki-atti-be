package com.hankkiatti.support;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.student.entity.DisabilityType;
import com.hankkiatti.domain.student.entity.Student;
import java.time.LocalDateTime;

/**
 * 계정에 붙일 장애학생·도우미 프로필. 저장하지 않은 상태로 만든다.
 * /api/me가 역할별 프로필을 읽기 때문에 통합 테스트에서는 계정과 함께 저장한다.
 */
public final class TestProfiles {

    private TestProfiles() {}

    // 장애학생은 로그인 아이디가 학번이다
    public static Student student(Account account) {
        return new Student(account, "김한끼", account.getLoginId(), "010-0000-0000", "kakao_student",
                account.getLoginId() + "@mju.ac.kr", DisabilityType.PHYSICAL, null);
    }

    // 도우미는 로그인 아이디가 가입 이메일이다
    public static Helper helper(Account account, String studentNo) {
        return new Helper(account, "이도움", studentNo, account.getLoginId(), "010-1111-1111", "kakao_helper", true,
                LocalDateTime.of(2026, 10, 1, 9, 0));
    }
}
