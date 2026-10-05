package com.hankkiatti.global.config;

import org.hibernate.dialect.MySQLDialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;

/**
 * enum 컬럼에 CHECK 제약을 만들지 않는 MySQL Dialect.
 * enum 값을 추가해도 ddl-auto가 기존 CHECK를 갱신하지 않아 새 값 INSERT가 실패하므로 컬럼 CHECK 생성을 끈다.
 */
public class NoColumnCheckMySQLDialect extends MySQLDialect {

    public NoColumnCheckMySQLDialect() {
        super();
    }

    // DB 버전 자동 감지를 유지하려면 Hibernate가 이 생성자로 접속 정보를 넘겨야 한다
    public NoColumnCheckMySQLDialect(DialectResolutionInfo info) {
        super(info);
    }

    @Override
    public boolean supportsColumnCheck() {
        return false;
    }
}
