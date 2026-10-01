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

## 프로필
| 프로필 | 용도 | 설정 |
|---|---|---|
| `local` (기본) | 로컬 개발 | `application-local.yml`, docker compose MySQL |
| `prod` | 배포 (현재는 팀 내부 개발 서버) | `application-prod.yml`, 환경 변수 `SPRING_DATASOURCE_*` 필요 |

## 패키지 구조
기능(도메인)별로 상위 폴더를 두고 그 아래에 계층을 나눕니다.

```
com.hankkiatti
├── domain
│   ├── common      # BaseTimeEntity 등 도메인 공통
│   └── {도메인}     # controller · dto · entity · exception · repository · service
└── global
    ├── config      # Security, JPA 설정
    ├── exception   # DomainException, GlobalExceptionHandler
    └── response    # ApiResponse, ApiResult, type/
```

자세한 규칙은 [AGENTS.md](AGENTS.md)를 참고하세요.
