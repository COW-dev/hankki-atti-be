package com.hankkiatti;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// 로그인은 JWT로 직접 구현하므로 기본 인메모리 사용자(생성 비밀번호 로그 출력)를 끈다
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class HankkiAttiApplication {

    public static void main(String[] args) {
        // 식사 시각·자동 완료 등 시간 기반 로직이 많아 서버 기본 타임존을 고정한다
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication.run(HankkiAttiApplication.class, args);
    }
}
