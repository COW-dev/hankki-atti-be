# AGENTS.md

이 파일은 AI 에이전트(Claude, Codex 등)가 이 레포지토리에서 작업할 때 따라야 할 규칙과 컨텍스트를 정의합니다.

---

## 프로젝트 개요

**한끼아띠 백엔드 API 서버** — 명지대학교 장애학생지원센터·장애학생 서포터즈 "아띠"와 협업하는 장애학생 식사 도우미 매칭 서비스.

장애학생이 학식당 식사 도움이 필요한 시간을 신청하면, 도우미 학생이 선착순으로 지원해 매칭되는 교내 웹 서비스다.

| 항목 | 내용 |
|---|---|
| 프레임워크 | Spring Boot 4.1.1 |
| Java | 21 |
| 빌드 도구 | Gradle (Groovy DSL), 단일 모듈 |
| 데이터베이스 | MySQL 8.4 (로컬은 docker-compose, 테스트는 H2) |
| 인증 | JWT + Spring Security (예정) |
| API 문서 | springdoc-openapi (Swagger UI) |

**사용자**

| 사용자 | 하는 일 | 화면 |
|---|---|---|
| 장애학생 | 도우미 신청, 매칭 확인, 취소·노쇼 신고 | 사용자 앱 (모바일 웹) · 계정은 센터가 발급 |
| 도우미 | 요청 보고 지원, 매칭·예비 확인, 취소 | 사용자 앱 (모바일 웹) · 직접 회원가입 |
| 관리자 (센터 직원·아띠 운영진) | 학생 등록, 현황·이력 조회, 도우미·공지 관리 | 관리자 페이지 (별도 도메인) |

**요구사항 원본**: Notion "장애학생지원센터 서비스 > 문서" (PRD, 기능명세서, 유저플로우, 미팅 노트). 명세와 코드가 다르면 Notion 기능명세서가 기준이며, 판단이 애매하면 추측하지 말고 사용자에게 확인한다.

---

## 빌드 / 테스트 / 실행 명령어

```bash
# 전체 빌드
./gradlew build

# 컴파일만 (빠른 확인)
./gradlew compileJava

# 테스트 실행
./gradlew test

# 로컬 실행 (Docker Desktop 실행 필요 — docker-compose.yml의 MySQL이 자동으로 뜬다)
./gradlew bootRun

# 빌드 결과물 정리
./gradlew clean
```

**최초 셋업 (clone 후 1회)**
```bash
bash scripts/setup-hooks.sh   # Git 훅 활성화 (main 직접 커밋 차단·시크릿 차단·커밋 메시지 자동 생성)
```
- 로컬에 gitleaks가 없으면 훅은 grep 폴백으로 동작한다 (`brew install gitleaks` 권장)

**프로필**

| 프로필 | 용도 |
|---|---|
| `local` (기본) | 로컬 개발. docker-compose MySQL, `ddl-auto: update` |
| `prod` | 배포. 현재는 팀 내부 개발 서버 용도라 Swagger 켜짐, `ddl-auto: update`. 실제 운영 전 Flyway + `validate`로 전환하고 Swagger를 끈다 |

---

## 패키지 / 모듈 구조

```
com.hankkiatti
├── HankkiAttiApplication.java
├── domain/                     # 비즈니스 도메인
│   ├── common/                 # BaseTimeEntity 등 도메인 공통
│   └── {도메인}/
└── global/                     # 공통/인프라
    ├── config/                 # Spring 설정 (Security, JPA Auditing)
    ├── exception/              # DomainException, GlobalExceptionHandler
    └── response/               # ApiResponse(팩토리), ApiResult(래퍼), type/
```

**도메인별 패키지 구성**
```
domain/{도메인}/
├── controller/
│   ├── admin/     # 관리자 API — AdminXxxController, AdminXxxControllerDocs
│   └── client/    # 사용자 앱 API — XxxController, XxxControllerDocs
├── dto/
│   ├── request/   # XxxRequestDto (record)
│   └── response/  # XxxResponseDto (record)
├── entity/        # 엔티티, 관련 Enum
├── exception/     # XxxException, XxxErrorType
├── repository/    # JpaRepository 확장
└── service/       # XxxService (admin/client 분리 시 하위 패키지)
```
- admin/client 중 한쪽만 있는 도메인은 하위 패키지 없이 `controller/`에 바로 둬도 된다
- 도메인 이름과 경계는 해당 기능을 처음 구현할 때 `/plan`에서 확정하고 이 섹션에 추가한다

---

## 코딩 컨벤션

- 들여쓰기 4칸 스페이스 (탭 금지)
- import는 와일드카드 없이 한 블록으로 알파벳 순 정렬

### 1. Request DTO

- 신규 Request DTO는 `record`로 작성
- DTO 이름은 `XxxRequestDto` 형식 (접미사 `Dto` 포함)
- Validation 어노테이션은 **반드시 별도 줄**에 배치 (한 줄 몰아쓰기 금지)

```java
// 올바른 예
@Schema(description = "도우미 신청 요청")
public record HelpRequestCreateRequestDto(

        @NotNull
        @Schema(description = "식사 시작 시각 (30분 단위)", example = "2026-10-05T12:00:00")
        LocalDateTime startAt,

        @NotEmpty
        @Schema(description = "필요한 도움", example = "[\"SERVING\", \"SEATING\"]")
        Set<HelpType> helpTypes
) {}

// 금지 — 한 줄에 몰아쓰기
public record HelpRequestCreateRequestDto(
    @NotNull @Schema(description = "식사 시작 시각") LocalDateTime startAt
) {}
```

---

### 2. Response DTO

- 신규 Response DTO는 `record`로 작성
- DTO 이름은 `XxxResponseDto` 형식 (접미사 `Dto` 포함)
- **`from(Entity)` 메서드 불필요** — 서비스 레이어에서 직접 생성
- Controller 레이어에 Entity 노출 금지 (Service → DTO 변환)

```java
@Schema(description = "도우미 신청 응답")
public record HelpRequestResponseDto(
        @Schema(description = "신청 ID") Long id,
        @Schema(description = "신청 상태") HelpRequestStatus status
) {}

// 서비스에서 직접 생성
return new HelpRequestResponseDto(request.getId(), request.getStatus());
```

---

### 3. 예외 처리

```
DomainException (abstract, global)
└── XxxException (도메인별 구체 예외)
    └── 생성자 인자: XxxErrorType (enum, ErrorCode 구현)
```

**규칙**
- `IllegalArgumentException`, `RuntimeException` 직접 throw 금지
- 새 도메인 예외 추가 시:
  1. `XxxErrorType` enum 생성 (`ErrorCode` 구현)
  2. `XxxException extends DomainException` 생성
  3. `throw new XxxException(XxxErrorType.XXX)` 사용
- `GlobalExceptionHandler`에 새 예외 타입을 추가할 필요 없음 — `DomainException` 핸들러가 자동 처리
- 내부 식별자 등 디버그 정보는 `detail` 인자로 넘긴다 (로그에만 남고 응답에는 노출되지 않음)

```java
// 올바른 예
throw new HelpRequestException(HelpRequestErrorType.NOT_FOUND);
throw new HelpRequestException(HelpRequestErrorType.ALREADY_MATCHED, "requestId=" + id);

// 금지
throw new IllegalArgumentException("신청을 찾을 수 없습니다.");
```

**ErrorType enum 작성 규칙**
```java
@Getter
@RequiredArgsConstructor
public enum HelpRequestErrorType implements ErrorCode {
    NOT_FOUND(404, "신청을 찾을 수 없습니다."),
    ALREADY_MATCHED(409, "이미 매칭된 신청입니다.");

    private final int httpStatusCode;
    private final String message;
}
```

---

### 4. 공통 응답 구조

`ApiResult<T>` — 실제 응답 본문 래퍼 (record: `resultType`, `httpStatusCode`, `message`, `data`)
`ApiResponse` — `ResponseEntity<ApiResult<T>>` 생성 팩토리 유틸
`SuccessType` — 성공 응답 타입 enum
`CommonErrorType` / `XxxErrorType` — 오류 응답 타입

```java
// 컨트롤러 반환 패턴
return ApiResponse.of(SuccessType.SUCCESS, service.getRequest(id));   // 데이터 있음
return ApiResponse.of(SuccessType.CREATED, service.create(req));      // 생성
return ApiResponse.of(SuccessType.SUCCESS);                           // 데이터 없음
```

**Swagger import 충돌 처리**
`io.swagger.v3.oas.annotations.responses.ApiResponse`와 프로젝트 `ApiResponse` 이름이 겹친다.
```java
// 규칙: 우리 ApiResponse는 import, Swagger @ApiResponse는 FQN
import com.hankkiatti.global.response.ApiResponse;

@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", ...)
ResponseEntity<ApiResult<HelpRequestResponseDto>> getRequest(...);
```

---

### 5. Entity 작성 규칙

- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 필수
- setter 금지, 상태 변경은 의미 있는 메서드로 (예: `match(...)`, `cancel(...)`)
- 생성자는 package-level 또는 `public` 생성자 사용 (static factory 불필요)
- `BaseTimeEntity` 상속 필수 (`createdAt`, `updatedAt` 자동 관리)
- Enum 필드는 `@Enumerated(EnumType.STRING)` 사용
- 시각 필드는 `LocalDateTime` 사용 (서버·DB 타임존은 Asia/Seoul로 고정되어 있다)

```java
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "help_requests")
public class HelpRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HelpRequestStatus status;

    // ... 필드

    public void cancel() { ... }   // setter 대신 의미 있는 메서드
}
```

---

### 6. Controller 작성 규칙

- `@RestController` + `@RequiredArgsConstructor`
- `@RequestMapping`으로 기본 경로 지정
  - 관리자 API: `/api/admin/...`
  - 사용자 앱 API: `/api/...`
- Swagger 문서는 `*ControllerDocs` 인터페이스로 분리, Controller가 `implements`
- 반환 타입: `ResponseEntity<ApiResult<T>>`

---

### 7. Service 작성 규칙

- `@Service` + `@RequiredArgsConstructor`
- 쓰기 메서드: `@Transactional` 개별 적용
- 읽기 메서드: `@Transactional(readOnly = true)` 개별 적용 (클래스 레벨 적용은 선택)
- 의존성 주입: 생성자 주입만 허용 (`@Autowired` 필드 주입 금지)
- admin/client 로직이 복잡하면 `service/admin/`, `service/client/` 패키지로 분리

---

## 도메인 핵심 규칙

기능명세서에서 코드 설계에 직접 영향을 주는 규칙만 추렸다. 상세는 Notion 기능명세서·유저플로우를 따른다.

- **선착순 즉시 매칭** — 신청 건에 처음 지원한 도우미가 바로 매칭되고, 이후 지원자는 예비 1·2·3번. 동시 지원은 서버 도착 순이므로 매칭 처리는 **반드시 락(비관적 락 등)이나 유니크 제약으로 동시성을 보장**한다
- **블라인드** — 매칭 전에는 도우미 응답에 학생 개인정보(이름·학번·연락처·장애 유형 등)를 **응답 DTO에서 아예 제외**한다. 프론트에서 숨기는 방식 금지
- **장애 정보는 민감정보** — 장애 유형·특이사항은 관리자 API에서만 조회 가능. 로그에 출력하지 않는다
- **즉시 취소, 관리자 승인 없음** — 매칭된 도우미가 취소하면 예비 1번 자동 승격, 예비가 없으면 모집 재개. 취소 사유·시점은 이력으로 남긴다
- **관리자는 매칭에 관여하지 않음** — 배정·재배정·취소 승인 API를 만들지 않는다
- **시간 기반 자동 처리** — 식사 시작 시각에 미매칭 건은 매칭 실패, 식사 종료(시작 + 1시간)에 이용 완료, 이후 24시간 동안 노쇼 신고 가능. 신청 시각은 30분 단위
- **시간 겹침** — 이용 시간이 1시간이므로 같은 슬롯이 아니라 **구간이 겹치는지**로 판단한다

---

## 테스트 컨벤션

**작성 의무**: 새 서비스 로직 또는 기존 로직 변경 시 해당 부분의 테스트를 반드시 함께 작성한다 — 구현 완료의 정의에 테스트가 포함된다.

**계층별 전략**

| 계층 | 방식 | 도구 |
|---|---|---|
| Service | 단위 테스트 (리포지토리/외부 의존성 mock) | JUnit 5 + Mockito |
| Repository | 슬라이스 테스트 | `@DataJpaTest` + H2 |
| Controller | 슬라이스 테스트 (필요 시) | `@WebMvcTest` |

**작성 규칙**
- 테스트 이름: `메서드명_상황_기대결과` (예: `apply_이미매칭된신청_예비번호부여`)
- 구조: given-when-then 주석으로 구분
- 검증 우선순위: 정상 케이스 1개 + 예외 케이스(도메인 예외 발생) 각 1개 이상
- 매칭·취소·승격처럼 동시성이 걸린 로직은 동시 요청 시나리오 테스트를 함께 작성한다
- 깡통 테스트 금지 — assertion 없는 테스트, 구현을 그대로 복사한 테스트는 작성하지 않는다
- 커버리지 수치를 올리기 위한 무의미한 getter/setter 테스트 금지

```java
@Test
void apply_이미매칭된신청_예비번호부여() {
    // given
    given(helpRequestRepository.findByIdForUpdate(1L)).willReturn(Optional.of(matchedRequest));

    // when
    ApplyResponseDto result = applyService.apply(helperId, 1L);

    // then
    assertThat(result.waitingOrder()).isEqualTo(1);
}
```

---

## Git 워크플로우

- 브랜치 전략: **GitHub flow** — `main`에서 작업 브랜치를 만들고 PR로 `main`에 병합한다. `develop` 브랜치는 운영 기간에 들어설 때 도입한다
- 브랜치 네이밍: `feat/기능명`, `fix/버그명`, `refactor/대상`, `chore/작업명`, `docs/대상` — 전부 `main`에서 분기
- 커밋 형식: Conventional Commits — `type: 한글 제목 (명사형 종결)`
  | type | 용도 |
  |---|---|
  | `feat` | 새 기능 추가 |
  | `fix` | 버그 수정 |
  | `refactor` | 동작 변경 없는 코드 구조 개선 |
  | `chore` | 빌드/설정/의존성 등 기타 작업 |
  | `docs` | 문서 변경 |
  | `test` | 테스트 추가/수정 |
  | `ci` | CI/CD 워크플로우 변경 |
  | `style` | 포맷팅 등 로직 변경 없는 스타일 수정 |
  ```
  feat: 도우미 신청 API 추가
  fix: 예비 도우미 승격 시 시간 겹침 검사 누락 수정
  ```
  글자수 제한은 두지 않는다 — type + 명사형 종결만 지키면 길이는 자연히 적정선에서 맞춰진다.
- 본문은 한글로, 무엇을 바꿨는지보다 **왜** 바꿨는지를 적는다
- **PR 병합 방식**: **Squash and merge** — PR 제목이 곧 `main`의 최종 커밋 메시지가 된다
- **PR 제목 컨벤션**: 커밋 제목과 동일한 형식(`type: 명사형 제목`)을 따른다
- PR은 `.github/pull_request_template.md` 형식 준수

---

## 절대 규칙

다음 행동은 어떤 상황에서도 금지된다.

1. **main 병합은 사람만** — PR 머지는 사용자가 직접 실행, 에이전트는 절대 병합하지 않는다
2. **force push 금지** — `git push --force` 절대 실행 금지
3. **main 직접 커밋/push 금지** — 작업은 항상 작업 브랜치에서 한다 (pre-commit 훅이 로컬에서 차단)
4. **민감정보 커밋 금지** — 비밀번호, JWT 시크릿, 운영 DB 접속 정보, 메일 계정 정보 등 커밋 금지. 설정 파일에는 `${ENV_VAR}` 플레이스홀더를 쓴다
5. **보안 변경 사람 리뷰 필수** — `SecurityConfig`, JWT 필터/토큰 관련 클래스 수정 시 반드시 사람이 리뷰 후 병합
6. **커밋 전 사용자 승인 필수** — 커밋 메시지 제안 후 승인 대기, 자동 커밋 금지

**허용되는 것**
- 작업 브랜치(`feat/*`, `fix/*` 등)로의 push — 커밋이 사용자 승인을 받았다면 허용
- `gh pr create --draft` — draft PR 생성 허용, 단 라벨(`agent:claude-code` 또는 `agent:codex`)을 붙인다

---

## AI 에이전트 커맨드 워크플로우

커맨드 원본은 `.agents/commands/`에 있다. `.claude/commands/`와 `.codex/commands/`는 이 디렉토리를 가리키는 심링크이므로, **수정은 반드시 `.agents/commands/`에서만** 한다.

아래 명령을 요청하면 `.agents/commands/<command>.md` 파일을 먼저 읽고 해당 절차를 따른다.

| 명령 | 파일 | 용도 |
|---|---|---|
| `/feature` | `.agents/commands/feature.md` | 계획부터 PR 초안까지 전체 기능 워크플로우 |
| `/plan` | `.agents/commands/plan.md` | 구현 전 계획 수립 |
| `/impl` | `.agents/commands/impl.md` | 승인된 계획 기반 구현 |
| `/review` | `.agents/commands/review.md` | 변경사항 셀프 리뷰 |
| `/commit` | `.agents/commands/commit.md` | 커밋 메시지 제안 및 승인 후 커밋 |
| `/pr` | `.agents/commands/pr.md` | PR 설명 초안 작성 및 draft PR 생성 |

**적용 규칙**
- 커맨드 파일 내용이 AGENTS.md와 충돌하면 AGENTS.md를 우선한다.
- 커맨드 파일을 읽었더라도 절대 규칙은 항상 유지한다.
- `/commit`은 커밋 메시지 제안 후 사용자 승인을 받은 경우에만 실행한다.
- `/pr`은 브랜치 push 후 draft PR 생성까지 실행한다. 머지는 하지 않는다.

---

## DB 스키마 변경 규칙

- 현재는 `ddl-auto: update`로 엔티티 변경이 자동 반영된다 (local·prod 모두)
- `update`는 컬럼 추가만 반영하고 컬럼 이름 변경·삭제·타입 변경은 반영하지 않는다 — 이런 변경이 있으면 PR 본문 "타 직군 전달 사항"에 DB 수동 조치 필요 여부를 적는다
- 실제 운영 전에 Flyway를 도입하고 prod를 `validate`로 전환한다. 도입 후에는 이 섹션을 마이그레이션 규칙으로 교체한다

---

## 핸드오프/상태 문서 컨벤션

에이전트가 작업 인계 문서(핸드오프, 계획, 상태 파일)를 작성할 때:

1. **기준점 기록 필수** — 문서 상단에 작성 시각, 기준 브랜치, HEAD SHA를 적는다
   ```
   > 작성: 2026-10-01 · 브랜치: feat/xxx · 기준 HEAD: abc1234
   ```
2. **완료 처리 규칙 명시** — 문서가 언제 효력을 잃는지, 완료 시 어떻게 처리할지(배너 후 아카이브 또는 삭제) 문서 안에 적는다
3. **완료된 핸드오프는 즉시 닫는다** — 체크리스트가 끝나면 상단에 `✅ 완료 (날짜, 병합 PR)` 배너를 달거나 삭제한다
4. **영구 정보는 이 파일(AGENTS.md)로 이관** — 일회성 문서에 영구 규칙을 남기지 않는다
