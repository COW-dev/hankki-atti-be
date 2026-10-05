# hankki-atti-be

명지대학교 장애학생 서포터즈 아띠와 협업하는 장애학우 식사 매칭 서비스 백엔드입니다.

## 기술 스택
- Java 21, Spring Boot 4.1, Gradle
- Spring Data JPA, MySQL 8.4
- Spring Security, springdoc-openapi (Swagger)

## 로컬 실행
Docker Desktop을 켠 상태에서 실행하면 `docker-compose.yml`의 MySQL 컨테이너가 자동으로 뜹니다.

```bash
./gradlew bootRun
```

- Swagger: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- 메일 확인 (Mailpit, 실제로 보내지 않음): http://localhost:8025

## 프로필
| 프로필 | 용도 | 설정 |
|---|---|---|
| `local` (기본) | 로컬 개발 | `application-local.yml`, docker compose MySQL |
| `prod` | 배포 (현재는 팀 내부 개발 서버) | `application-prod.yml`, 환경 변수 `SPRING_DATASOURCE_*` 필요 (`JWT_SECRET`, `CORS_ALLOWED_ORIGINS`는 선택) |

| 환경 변수 | 설명 |
|---|---|
| `JWT_SECRET` | 선택. access 토큰 서명 키, Base64로 인코딩된 32바이트 이상 (`openssl rand -base64 32`). 비우면 기동할 때마다 임의 키를 쓴다 — 재시작 시 access 토큰만 무효가 되고 refresh로 다시 받으므로 사용자는 다시 로그인하지 않는다. 서버를 2대 이상 띄우면 필수 |
| `CORS_ALLOWED_ORIGINS` | 프론트 주소. 쉼표로 구분한 정확한 출처 (예: `https://app.bluerack.org,https://admin.bluerack.org`). 로컬 기본값 `http://localhost:3000`, prod는 비어 있으면 브라우저의 다른 출처 요청을 모두 막는다 |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | 메일 발송 (Gmail: `smtp.gmail.com`, `587`, 서비스 전용 Gmail 주소, 앱 비밀번호). 없으면 메일을 보내지 않고 발송 대기로 쌓아 두었다가 설정 후 보낸다 |

## Docker 이미지
main에 병합되고 CI가 통과하면 `mjucow/hankki-atti-be:vX.Y.Z`(+ `latest`)가 Docker Hub에 올라갑니다. 버전·배포 규칙은 [AGENTS.md — 배포 (CD)](AGENTS.md#배포-cd)를 참고하세요.

```bash
docker run -d -p 8080:8080 \
  -e SPRING_DATASOURCE_URL='jdbc:mysql://<host>:3306/hankki_atti_db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8' \
  -e SPRING_DATASOURCE_USERNAME=<user> \
  -e SPRING_DATASOURCE_PASSWORD=<password> \
  -e CORS_ALLOWED_ORIGINS=<프론트 주소> \
  mjucow/hankki-atti-be:latest
```

## 관리자 계정 만들기
관리자 계정 관리 화면이 아직 없어 SQL로 만듭니다. 방법은 [scripts/seed-admin.sql](scripts/seed-admin.sql) 맨 위 주석에 있습니다. 비밀번호 원문과 해시는 커밋하지 않습니다.

## 패키지 구조
기능(도메인)별로 상위 폴더를 두고 그 아래에 계층을 나눕니다.

```
com.hankkiatti
├── domain
│   ├── common      # BaseTimeEntity 등 도메인 공통
│   └── {도메인}     # controller · dto · entity · exception · repository · service
└── global
    ├── config      # Security, JPA, JWT 설정
    ├── security    # JWT 발급·검증, refresh 토큰 쿠키, 인증 오류 응답
    ├── exception   # DomainException, GlobalExceptionHandler
    └── response    # ApiResponse, ApiResult, type/
```

자세한 규칙은 [AGENTS.md](AGENTS.md)를 참고하세요.
