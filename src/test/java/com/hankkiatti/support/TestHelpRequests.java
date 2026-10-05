package com.hankkiatti.support;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.student.entity.DisabilityType;
import com.hankkiatti.domain.student.entity.Student;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 신청·지원 테스트용 엔티티. 저장하지 않은 상태로 만든다.
 */
public final class TestHelpRequests {

    private TestHelpRequests() {}

    public static Student student(String studentNo) {
        Account account = new Account(studentNo, "hash", AccountRole.STUDENT, false, false);
        return new Student(account, "학생" + studentNo, studentNo, "010-0000-0000", "kakao" + studentNo,
                studentNo + "@mju.ac.kr", DisabilityType.PHYSICAL, null);
    }

    public static Helper helper(String studentNo) {
        Account account = new Account(studentNo + "@mju.ac.kr", "hash", AccountRole.HELPER, false, false);
        return new Helper(account, "도우미" + studentNo, studentNo, studentNo + "@mju.ac.kr", "010-1111-1111",
                "kakao" + studentNo, true, LocalDateTime.of(2026, 10, 1, 9, 0));
    }

    public static HelpRequest request(Student student, LocalDateTime startAt) {
        return new HelpRequest(student, startAt, Set.of(HelpType.SERVING), null, null);
    }
}
