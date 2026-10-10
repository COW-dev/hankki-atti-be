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
│   ├── common/                 # BaseTimeEntity, LabeledEnum, AbstractEnumConverter 등 도메인 공통
│   └── {도메인}/
└── global/                     # 공통/인프라
    ├── config/                 # Spring 설정 (Security, JPA Auditing, MySQL Dialect, JWT, Clock)
    ├── security/               # JWT 발급·검증, 인증 주체(AuthPrincipal), refresh 토큰 쿠키, 인증 오류 응답
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

**도메인 목록**
```
domain/
├── common/       BaseTimeEntity, LabeledEnum, AbstractEnumConverter, PhoneNumbers, 공통 검증(@PhoneNumber·@KakaoId)
├── account/      Account, AccountRole, AccountStatus
├── auth/         RefreshToken, PasswordResetToken, TokenAudience, 로그인·토큰·비밀번호 변경·재설정 API
├── mail/         MailOutbox, 메일 아웃박스 적재·발송(MailOutboxService, MailRelay)
├── sms/          SmsOutbox, 문자 아웃박스 적재·발송(SmsOutboxService, SmsRelay), 발송부 SmsSender(AWS SNS 구현 SnsSmsSender)
├── student/      Student, DisabilityType, CredentialMailStatus
├── helper/       Helper, 도우미 회원가입(HelperSignupService, 공개 경로 `/api/helpers/signup`)
├── admin/        Admin, AdminGrade
├── helprequest/  HelpRequest, HelpType, HelpRequestStatus, RequestCancelType, Meal, 신청 가능 날짜·시각(HelpRequestSchedule), 식사 시작·종료 자동 처리(MealTimeJob)
└── application/  Application, ApplicationStatus(ACTIVE·CONFIRMED), CancelReason, ApplicationAfterAction, 지원(ApplicationService), 도우미 매칭 취소(HelperCancelService), 다음 예비 승격·모집 재개(WaitingPromoter — 취소·승격 거절·응답 마감이 같이 씀), 식사 1시간 이내 승격의 수락·거절·응답 마감 자동 거절(PromotionResponseService), 매칭 현황 조회(MyApplicationService, MyApplicationFilter), 확정 매칭 시 겹치는 다른 예비 자동 제외(HelperConfirmedEvent → OverlappingWaitExcluder), 지원 가능 규칙(ApplyPolicy — 요청 목록 카드·지원 검증·승격 후보 확인이 같이 씀), 지원 결과 예상 ApplyOutcome·ApplyBlockReason(저장 안 함)
```
- `Student`·`Helper`·`Admin`은 `Account`와 PK를 공유하는 1:1 프로필이다 (`@MapsId`)

---

## 코딩 컨벤션

- 들여쓰기 4칸 스페이스 (탭 금지)
- import는 와일드카드 없이 한 블록으로 알파벳 순 정렬

### 1. Request DTO

- 신규 Request DTO는 `record`로 작성
- DTO 이름은 `XxxRequestDto` 형식 (접미사 `Dto` 포함)
- Validation 어노테이션은 **반드시 별도 줄**에 배치 (한 줄 몰아쓰기 금지)
- 공통 규칙이 있는 값은 공통 검증 어노테이션을 쓴다: 비밀번호 `@Password`, 휴대전화 `@PhoneNumber`, 카톡 ID `@KakaoId`. null은 통과시키므로 `@NotBlank`를 함께 붙이고, 전화번호는 `PhoneNumbers.normalize`로 하이픈 형식(010-1234-5678)으로 맞춰 저장한다

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

`ApiResult<T>` — 실제 응답 본문 래퍼 (record: `resultType`, `httpStatusCode`, `code`, `message`, `data`)
`ApiResponse` — `ResponseEntity<ApiResult<T>>` 생성 팩토리 유틸
`SuccessType` — 성공 응답 타입 enum
`CommonErrorType` / `XxxErrorType` — 오류 응답 타입

**오류 코드 (`code`)** — 실패 응답에만 있다. 프론트는 메시지 문구가 아니라 이 값으로 오류를 구분한다
- `ErrorCode.getCode()`가 "{타입 이름}_{상수 이름}"으로 자동으로 만든다: `AuthErrorType.PASSWORD_CHANGE_REQUIRED` → `AUTH_PASSWORD_CHANGE_REQUIRED`, `CommonErrorType.NOT_FOUND` → `COMMON_NOT_FOUND`
- 따로 코드를 적지 않는다. 대신 `XxxErrorType` 클래스·상수 이름을 바꾸면 API 변경이므로 PR 본문 "타 직군 전달 사항"에 적는다

**성공 응답의 `data`** — 상태를 바꾸는 API(POST·PATCH)도 바뀐 리소스를 `data`로 돌려준다 (예: 신청 철회 → 철회된 신청). 화면이 다시 조회하지 않고 바로 반영할 수 있게 하려는 것이다. 돌려줄 리소스가 없거나(로그아웃) 무엇을 돌려줘도 정보가 드러나는 경우(비밀번호 재설정 요청)만 데이터 없이 응답한다

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
- enum은 autoApply 컨버터로 VARCHAR 저장, `@Enumerated` 쓰지 않음 — `@Enumerated`는 MySQL에서 네이티브 `ENUM` 컬럼·값 목록 CHECK를 만들고, `ddl-auto: update`는 enum 값이 추가돼도 이를 갱신하지 않아 새 값 INSERT가 실패하기 때문
  - enum마다 같은 패키지에 `XxxConverter extends AbstractEnumConverter<Xxx>` + `@Converter(autoApply = true)`를 둔다. 필드에는 `@Column(length = ...)`만 붙인다
  - MySQL은 커스텀 Dialect(`NoColumnCheckMySQLDialect`)로 CHECK를 끈다 — 컨버터를 써도 Hibernate가 enum 값 목록으로 CHECK를 만들기 때문
  - enum은 `LabeledEnum`을 구현해 한글 `label`을 둔다 (메일·알림 등 서버가 만드는 문장용)
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

    @Column(nullable = false, length = 20)   // HelpRequestStatusConverter가 자동 적용
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
- 현재 시각은 `LocalDateTime.now(clock)` — `Clock` 빈을 주입받는다 (테스트에서 시각 고정)

---

### 8. 인증·인가

- 로그인한 사용자는 컨트롤러에서 `@AuthenticationPrincipal AuthPrincipal principal`로 받는다 (`accountId`, `role`, `audience`)
- 경로별 권한은 `SecurityConfig`에서 정한다: `/api/admin/**`은 관리자, 그 밖의 `/api/**`는 장애학생·도우미. 새 API는 이 규칙을 따르면 따로 설정할 것이 없다
- 로그인 없이 열어야 하는 API는 `AuthPaths`에 추가한다 (`SecurityConfig` 수정 → 사람 리뷰 필수)
- 관리자 권한 등급(전체/제한)은 토큰에 넣지 않는다. 등급이 필요한 API는 서비스에서 `Admin`을 조회해 확인한다
- 비밀번호 규칙 검증은 `@Password` (+ `@NotBlank`)를 쓴다

---

### 9. 메일·비동기·예약 작업

- 메일은 `MailOutboxService.enqueue(...)`로 아웃박스에 적기만 한다. SMTP(`JavaMailSender`)를 직접 부르지 않는다
  - 업무 트랜잭션 안에서 부르면 업무가 롤백될 때 메일도 사라진다. 커밋되면 메일 전용 스레드가 바로 보내고, 놓친 메일은 폴러가 10초마다 보낸다
  - 실패하면 1분 간격으로 3번 재시도, 그래도 실패면 `MailFailedEvent` 발행. 발송 결과가 필요한 기능은 `MailSentEvent`·`MailFailedEvent`를 구독한다
  - 수신자·본문은 로그에 남기지 않는다 (아웃박스 id·종류만)
- 문자는 `SmsOutboxService.enqueue(...)`로 아웃박스에 적는다. 메일과 같은 흐름(커밋 후 발송·재시도·`SmsFailedEvent`)이고, 발송부는 `SmsSender` 인터페이스라 발신 서비스를 바꿀 때 구현체만 교체한다. 문자가 주 알림 채널이지만 메일도 쓸 수 있으니 두 모듈을 합치거나 없애지 않는다
  - 전화번호는 저장 형식(010-1234-5678)으로 넘기면 E.164(+821012345678)로 바꿔 보낸다. 본문은 `[한끼아띠]`로 시작, 45자 안팎, 링크 없음
- 예약 작업은 `@Scheduled`로 만든다. 스케줄러 스레드는 4개(`spring.task.scheduling.pool.size`) — 한 작업이 오래 걸려도 다른 작업이 밀리지 않게 작업 안에서 오래 막히는 호출을 피한다
- 비동기 작업은 용도별 스레드 풀을 따로 둔다 (`@Async("mailExecutor")`처럼 이름 지정). 이름 없는 `@Async`는 쓰지 않는다
- 서버가 여러 대일 수 있으므로 예약 작업은 같은 대상을 두 서버가 동시에 처리해도 안전해야 한다 (조건부 UPDATE로 선점 등)
- 시간 기반 자동 처리는 "시각이 지났는데 상태가 그대로인 건"을 찾아 처리한다 (놓친 건도 다음 실행에서 따라잡는다). 대상 ID만 먼저 조회하고, 한 건씩 별도 트랜잭션에서 `findByIdForUpdate`로 잠근 뒤 상태·시각을 다시 확인하고 바꾼다 — 사용자 요청과 겹쳐도 순서가 맞고, 한 건 실패가 다른 건에 번지지 않는다
- 신청 상태를 바꾸는 작업(지원·취소·승격·자동 처리)은 모두 `HelpRequestRepository.findByIdForUpdate`로 신청 행을 잠근다
- 지원 행·도우미 행도 잠가야 하는 작업(지원 — 같은 도우미의 겹치는 지원을 한 줄로 세움, 도우미 취소·예비 승격 등)은 **신청 → 지원 → 도우미** 순서로 잠근다(`ApplicationRepository.findByIdForUpdate`·`findWaitingForUpdate`, `HelperRepository.findByIdForUpdate`). 순서가 어긋나면 교착이 생긴다. 잠금은 트랜잭션의 첫 쿼리로 둔다 — MySQL(REPEATABLE READ)에서 잠금 뒤 첫 일반 조회가 스냅샷이 되어야 먼저 커밋된 다른 요청까지 보인다
  - 한 신청을 처리하다가 **다른 신청의 지원**을 바꿔야 하면(매칭된 도우미의 겹치는 다른 예비 자동 제외 등) 같은 트랜잭션에서 하지 않는다 — 이미 쥔 도우미 락 뒤에 다른 신청을 잠그면 순서가 거꾸로 된다. 이벤트를 발행하고 커밋 뒤 한 건씩 새 트랜잭션에서 그 신청부터 잠가 처리한다 (`HelperConfirmedEvent` → `OverlappingWaitExcluder`)
  - 지원 ID로 시작하는 작업(도우미 취소)은 신청 ID를 알려고 먼저 읽어야 해서 첫 쿼리를 잠금으로 둘 수 없다. 이런 트랜잭션은 `@Transactional(isolation = READ_COMMITTED)`로 두고, 잠그기 전에는 엔티티가 아니라 값(신청 ID)만 읽는다 — 먼저 읽은 엔티티는 잠근 뒤 다시 조회해도 처음 읽은 상태가 쓰인다

---

## 도메인 핵심 규칙

기능명세서에서 코드 설계에 직접 영향을 주는 규칙만 추렸다. 상세는 Notion 기능명세서·유저플로우를 따른다.

- **선착순 즉시 매칭** — 신청 건에 처음 지원한 도우미가 바로 매칭되고, 이후 지원자는 예비 1·2·3번. 동시 지원은 서버 도착 순이므로 매칭 처리는 **반드시 락(비관적 락 등)이나 유니크 제약으로 동시성을 보장**한다
- **블라인드** — 매칭 전에는 도우미 응답에 장애학생 개인정보(이름·학번·연락처·장애 유형 등)를 **응답 DTO에서 아예 제외**한다. 프론트에서 숨기는 방식 금지. 요청 목록(`OpenHelpRequestService`)은 신청 ID·시각·도움 유형만 주고, 기타 도움 내용·메모(장애 관련 내용이 들어갈 수 있다)·예비 인원도 주지 않는다. 카드마다 지금 지원하면 어떻게 되는지(`ApplyOutcome`: 바로 매칭·예비·불가 + `ApplyBlockReason`)를 함께 준다 — 재지원 불가는 진행 중 지원(`ApplicationStatus.ACTIVE`), 시간 겹침 차단은 확정 매칭(`CONFIRMED` = 매칭 완료·승격 응답 대기) 기준이고 예비끼리 겹치는 건 막지 않는다
- **장애 정보는 민감정보** — 장애 유형·특이사항은 관리자 API에서만 조회 가능. 로그에 출력하지 않는다
- **즉시 취소, 관리자 승인 없음** — 매칭된 도우미가 취소하면 예비 1번 자동 승격, 예비가 없으면 모집 재개. 취소 사유·시점은 이력으로 남긴다
- **1시간 이내 승격은 응답을 받는다** — 식사 1시간 이내에 승격되면 응답 대기(`PROMOTION_PENDING`). 마감은 식사 15분 전이고, 그보다 늦게 승격되면 식사 시작까지 기다린다. 마감이 지나면 자동 거절 → 다음 예비, 식사 시작까지 답이 없으면 매칭 실패. 겹치는 다른 예비 자동 제외는 수락할 때 한다
- **관리자는 매칭에 관여하지 않음** — 배정·재배정·취소 승인 API를 만들지 않는다
- **시간 기반 자동 처리** — 식사 시작 시각에 미매칭 건은 매칭 실패, 식사 종료(시작 + 1시간)에 이용 완료, 이후 24시간 동안 노쇼 신고 가능. 신청 시각은 30분 단위
- **신청 가능 날짜·시각** — 오늘부터 7일 뒤까지, 주말·공휴일 제외, 오늘은 시작 전인 시각만. 시작 시각(점심 11:30~13:00·저녁 17:00~17:30)과 공휴일 목록은 `HelpRequestSchedule`·`Meal` 한 곳에 둔다. 공휴일 목록은 매년(월력요항 발표·임시공휴일 지정 때) 갱신한다
- **시간 겹침** — 이용 시간이 1시간이므로 같은 슬롯이 아니라 **구간이 겹치는지**로 판단한다. 장애학생이 자기 모집 중·매칭 완료 신청과 겹치는 시간에 또 신청하면 막는다 (409 `HELP_REQUEST_TIME_OVERLAP`)

---

## 테스트 컨벤션

**작성 의무**: 새 서비스 로직 또는 기존 로직 변경 시 해당 부분의 테스트를 반드시 함께 작성한다 — 구현 완료의 정의에 테스트가 포함된다.

**커버리지 기준** (라인 커버리지, JaCoCo)

| 기준 | 수치 | 검사 위치 |
|---|---|---|
| PR 변경 코드 | 80% 이상 | CI `test` job의 diff-cover |
| 프로젝트 전체 | 70% 이상 | `./gradlew check` (`build`에 포함) — 로컬에서도 실패한다 |

- 측정 제외: `HankkiAttiApplication`, `global/config/**` — 제외 대상을 늘릴 때는 PR에 이유를 적는다
- 리포트: `./gradlew test` 후 `build/reports/jacoco/test/html/index.html`
- 기준 수치는 Google "Code Coverage Best Practices"(전체 75% commendable, 변경 코드 중심 관리)를 참고했다. 수치를 맞추기 위한 테스트가 아니라 동작을 검증하는 테스트를 쓴다

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
- PR은 CI(`.github/workflows/ci.yml`의 `test`, `secret-scan`, `agent-config`)가 모두 통과해야 병합할 수 있다 — `test`에는 커버리지 기준(변경 80%, 전체 70%)이 포함된다

---

## 배포 (CD)

main에 병합되고 CI 검사(`test`, `secret-scan`, `agent-config`)가 모두 통과하면 Docker 이미지를 Docker Hub에 push한다.

| 항목 | 내용 |
|---|---|
| 이미지 | `mjucow/hankki-atti-be` (Docker Hub, private · 동아리 계정) |
| 태그 | `vX.Y.Z` + `latest`. 버전 태그는 Docker Hub에서 불변(덮어쓰기 불가)으로 설정돼 있다 |
| 플랫폼 | `linux/amd64`, `linux/arm64` |
| Secrets | `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN` (Docker Hub Personal access token, Read & Write) |
| 워크플로우 | `.github/workflows/ci.yml`의 `version` → `docker` job, 버전 계산은 `.github/scripts/release-version.sh` |

**버전 규칙**
- 병합마다 마이너를 올린다 (`v0.1.0` → `v0.2.0`). 마이너는 상한 없이 증가한다 (`v0.9.0` → `v0.10.0`)
- 마지막 버전 이후 이미지에 들어가는 파일(`src/main`, `build.gradle`, `settings.gradle`, `gradle/`, `gradlew`, `Dockerfile`, `.dockerignore`)이 바뀌지 않았으면 배포하지 않는다 — 문서·CI·테스트만 바뀐 병합은 버전이 오르지 않는다
- 메이저(`v1.0.0`)는 정식 운영 시작 때 사람이 main 커밋에 태그를 직접 찍는다. 이후 자동으로 `v1.1.0`부터 이어진다
- git 태그를 먼저 push해 버전을 선점한 뒤 이미지를 push한다. 실패한 실행을 재실행하면 같은 버전으로 다시 시도한다

**push 전 스모크 테스트**: 단위 테스트는 H2로 돌기 때문에, `docker` job에서 MySQL 8.4를 띄우고 이미지를 `prod` 프로필로 실행해 `/actuator/health`가 UP인지 확인한다. 실패하면 push하지 않는다.

**이미지 실행에 필요한 환경 변수**: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`. 기본 프로필은 `prod`, 시간대는 Asia/Seoul로 고정, 포트 8080.
문자 발송(선택): `SMS_SNS_ENABLED=true`, `SMS_SNS_REGION`(기본 ap-northeast-2), `AWS_ACCESS_KEY_ID`·`AWS_SECRET_ACCESS_KEY`(`sns:Publish`만 허용한 IAM 키). 꺼져 있으면 문자는 발송 대기로 남는다.

**롤백**: 서버에서 이전 버전 태그를 pull해 다시 띄운다 (`docker pull mjucow/hankki-atti-be:vX.Y.Z`).

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
