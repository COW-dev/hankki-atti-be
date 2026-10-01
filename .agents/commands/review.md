# /review - 변경사항 셀프 리뷰

## 역할
구현된 변경사항을 AGENTS.md 기준으로 셀프 리뷰한다.
FAIL 항목은 수정안을 제시하고 사용자 승인을 받는다.

> 체크리스트 기준 상세 → **AGENTS.md — 코딩 컨벤션 섹션 참고**

## 실행 순서

### 1. 변경사항 파악

브랜치에서 이미 커밋한 변경까지 포함해 리뷰한다 — 미커밋 diff만 보면 커밋 후 실행 시 리뷰가 빈다.

```bash
git fetch origin main
git diff origin/main...HEAD   # 브랜치의 커밋된 변경 전체
git diff HEAD                 # 아직 커밋하지 않은 변경
```

두 diff 모두 비어있으면 즉시 다음 메시지를 출력하고 종료한다:
```
리뷰할 변경사항이 없습니다.
```

### 2. 체크리스트 항목별 검토

각 항목에 대해 **PASS / FAIL / N/A** 와 근거를 작성한다.

---

**[컨벤션]**
- [ ] 신규 Request DTO가 `record`로 작성됐는가 (이름: `XxxRequestDto`)
- [ ] 신규 Response DTO가 `record`로 작성됐는가 (이름: `XxxResponseDto`)
- [ ] Validation 어노테이션이 별도 줄에 배치됐는가
- [ ] `IllegalArgumentException`, `RuntimeException` 직접 throw가 없는가
- [ ] 새 예외가 `XxxException(XxxErrorType.XXX)` 패턴을 따르는가
- [ ] `XxxErrorType`이 `ErrorCode`를 구현하고 적절한 HTTP 상태 코드를 사용하는가
- [ ] Swagger import 충돌: `com.hankkiatti.global.response.ApiResponse` import, Swagger `@ApiResponse`는 FQN 사용했는가
- [ ] Entity에 setter가 추가되지 않았는가
- [ ] 신규 Entity가 `BaseTimeEntity`를 상속하는가
- [ ] Entity Enum 필드에 `@Enumerated(EnumType.STRING)`이 적용됐는가
- [ ] Controller가 `ResponseEntity<ApiResult<T>>` 형식을 반환하는가
- [ ] Controller가 `*ControllerDocs` 인터페이스를 implements 했는가
- [ ] admin API 경로가 `/api/admin/...`인가, 사용자 앱 API가 `/api/...`인가
- [ ] 들여쓰기 4칸 스페이스, 와일드카드 import 없음

**[도메인 규칙]**
- [ ] 매칭 전 도우미에게 가는 응답 DTO에 학생 개인정보(이름·학번·연락처·장애 유형)가 없는가 (블라인드)
- [ ] 장애 유형·특이사항이 관리자 API 외 응답이나 로그에 노출되지 않는가
- [ ] 매칭·취소·승격 등 동시 요청이 가능한 로직에 락 또는 유니크 제약이 있는가
- [ ] 시간 겹침을 슬롯 일치가 아니라 구간 겹침으로 판단하는가
- [ ] 시각 필드가 `LocalDateTime`인가

**[N+1 쿼리]**
- [ ] 연관 엔티티 조회 시 `fetch join` 또는 `@EntityGraph` 적용 여부 확인
- [ ] 반복문 내부에서 repository 메서드를 호출하는 코드가 없는가

**[트랜잭션 경계]**
- [ ] 여러 저장소 작업이 하나의 트랜잭션 내에 있는가
- [ ] 쓰기 메서드에 `@Transactional`이 적용됐는가
- [ ] 메일 발송 등 외부 호출이 트랜잭션 메서드 안에 혼재하지 않는가

**[예외 처리]**
- [ ] 발생 가능한 예외가 모두 처리됐는가
- [ ] 새로운 `XxxErrorType`이 적절한 HTTP 상태 코드를 사용하는가
- [ ] `GlobalExceptionHandler`에 별도 추가가 필요한 예외 타입이 없는가 (`DomainException` 계층이면 자동 처리)

**[테스트 커버리지]**
- [ ] 핵심 비즈니스 로직에 대한 테스트가 작성됐는가
- [ ] 경계값, 예외 케이스에 대한 테스트가 있는가

**[DTO / Entity 경계]**
- [ ] Entity가 Controller 레이어까지 노출되지 않는가
- [ ] Service가 Entity → DTO 변환 책임을 갖는가

**[보안]**
- [ ] 인증이 필요한 엔드포인트에 `SecurityConfig`의 인가 설정이 추가됐는가
- [ ] 민감정보(비밀번호, 토큰)가 Response DTO에 포함되지 않는가
- [ ] 보안 관련 파일(`SecurityConfig`, JWT 필터/토큰 관련 클래스)이 수정됐다면 리뷰 필요 명시

**[코드 정리]**
- [ ] 사용하지 않는 import가 없는가
- [ ] 주석 처리된 코드가 없는가
- [ ] TODO 주석이 남아있다면 명시

---

### 3. 결과 보고

**FAIL 항목 처리**
- FAIL인 항목에 대해 구체적인 수정 코드 제안
- 사용자 승인 후 수정 진행

**보고 형식**
```
[컨벤션] PASS
[N+1 쿼리] PASS
[트랜잭션 경계] N/A - 단순 조회만 포함
[예외 처리] FAIL - HelpRequestService에서 RuntimeException 직접 사용. 수정안: ...
...

총 FAIL: X건 - 수정안 검토 후 승인 부탁드립니다.
```

## 주의사항
- 코드를 직접 수정하지 않는다. 리뷰 -> 승인 -> 수정 순서를 지킨다
- PASS로 처리하기 애매한 항목은 FAIL로 처리하고 사용자에게 판단을 맡긴다
