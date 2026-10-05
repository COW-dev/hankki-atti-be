-- 관리자 계정 만들기 (MVP에는 관리자 계정 관리 화면이 없다)
--
-- 1) 비밀번호 해시 만들기 — 비밀번호 원문과 해시는 이 파일에 적어 커밋하지 않는다
--      htpasswd -bnBC 10 "" '비밀번호' | tr -d ':\n'
--    (macOS·리눅스 기본 htpasswd. 결과는 $2y$10$... 형태이며 Spring BCryptPasswordEncoder가 그대로 읽는다)
--
-- 2) 아래 네 값을 넣어 실행
--      mysql --default-character-set=utf8mb4 -h <호스트> -u <사용자> -p <DB> \
--        -e "SET @login_id='center01', @password_hash='<해시>', @name='김센터', @grade='FULL'; SOURCE scripts/seed-admin.sql;"
--    --default-character-set=utf8mb4가 없으면 한글 이름이 깨져 저장된다
--    @grade: FULL(전체 권한 — 센터 직원·아띠 회장단) / LIMITED(제한 권한 — 나머지 운영진)
--
-- 같은 login_id가 이미 있으면 accounts의 UNIQUE 제약으로 실패하고 아무것도 만들어지지 않는다.

START TRANSACTION;

INSERT INTO accounts (login_id, password_hash, role, status, must_change_password, accessibility_mode,
                      token_version, created_at, updated_at)
VALUES (@login_id, @password_hash, 'ADMIN', 'ACTIVE', FALSE, FALSE, 0, NOW(), NOW());

INSERT INTO admins (account_id, name, grade, created_at, updated_at)
VALUES (LAST_INSERT_ID(), @name, @grade, NOW(), NOW());

COMMIT;
