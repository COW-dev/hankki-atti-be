# 배포 저장소 연결

BE 코드의 main은 PR로만 수정한다. 이미지 업로드 후 CI는 별도 배포 저장소의
`k8s/be/atti-be.yaml`만 갱신하며, Argo CD는 그 저장소의 main을 감시한다.
배포 저장소는 BE와 FE가 함께 쓰며 서비스별 디렉터리를 나눈다.

## 1. 배포 저장소 생성

GitHub 오른쪽 위 `+` → `New repository`에서 다음처럼 생성한다.

- Owner: `COW-dev`
- Repository name: `hankki-atti-deploy` (다른 이름도 가능)
- Visibility: Public (민감정보는 저장하지 않는다)
- Add README: 켜기 (main 브랜치 초기화)

IntelliJ에서는 `Git` → `Clone`에서 새 저장소 주소를 입력하고, 로컬 경로를
BE 프로젝트와 나란한 `C:\Users\hughr\Desktop\code\hankki-atti-deploy`로 지정한다.
열기 방식은 새 창을 선택한다.

배포 저장소는 다음 구조로 관리한다. FE 파일은 FE 담당자가 실제 매니페스트를 추가할 때 만든다.

```text
hankki-atti-deploy/
├── README.md
└── k8s/
    ├── be/
    │   └── atti-be.yaml
    └── fe/
        └── (FE 매니페스트)
```

이 저장소의 `k8s/be/atti-be.yaml`에 BE 저장소의 `../../hankki-atti-deploy/k8s/be/atti-be.yaml`을 복사한다.
이미지 태그는 실제 업로드된 버전(현재 실패 실행의 `v0.50.0` 등)으로 지정한다.
ConfigMap과 Secret은 기존 클러스터의 것을 참조하며, 실제 자격증명은 복사하지 않는다.
BE 저장소의 `../../hankki-atti-deploy/k8s/be/atti-be.yaml`은 초기 복사용으로만 남고, 이후 변경은 배포 저장소에서 한다.

테스트 환경용 배포 저장소의 main은 배포 키로 직접 push할 수 있어야 한다.
조직 규칙이 새 저장소에도 PR을 강제한다면 인프라 담당자와 적용 대상을 조정한다.
BE main 보호 규칙은 변경하지 않는다.

BE CI는 `k8s/be`만, FE CI는 `k8s/fe`만 갱신한다. 두 CI의 push가 겹쳐 거절되면
실패한 배포 작업을 재실행해 최신 main에서 다시 반영한다. force push는 하지 않는다.

## 2. 저장소 전용 SSH 키 등록

개인 SSH 키와 별도로 로컬 터미널에서 전용 키를 만든다. 저장 위치는 저장소 밖으로 지정하고,
자동화용이므로 passphrase 질문에는 Enter를 누른다.

```powershell
ssh-keygen -t ed25519 -C "hankki-atti-deploy" -f "$env:TEMP/hankki-atti-deploy"
```

1. 배포 저장소 → `Settings` → `Deploy keys` → `Add deploy key`
2. Title: `BE CI deploy`
3. Key: 생성된 `hankki-atti-deploy.pub` 파일 내용 (공개키)
4. `Allow write access` 체크 → 저장
5. BE 저장소 → `Settings` → `Secrets and variables` → `Actions` → `Secrets`
6. `DEPLOY_SSH_KEY` 이름으로 확장자가 없는 `hankki-atti-deploy` 파일 전체 내용 (개인키) 등록
7. 같은 화면의 `Variables`에 `DEPLOY_REPOSITORY` = `COW-dev/hankki-atti-deploy` 등록

개인키는 채팅·커밋에 넣지 않는다. CI는 이 키로 배포 저장소에만 쓰고,
GHCR 이미지 push는 기존 `GITHUB_TOKEN`을 사용한다.
FE 연결 시에는 FE 전용 키 쌍을 별도로 만들어 같은 방식으로 등록한다.
Deploy key의 쓰기 권한은 저장소 전체이므로 서비스별 경로 제한은 각 CI에서 지킨다.

## 3. Argo CD 감시 대상 변경

기존 Application의 리소스 이름과 namespace를 유지한 채 소스만 변경한다.
새 Application으로 같은 리소스를 동시에 관리하지 않는다.

- Repository URL: 새 배포 저장소 주소
- Target revision: `main`
- Path: `k8s/be` (FE Application은 `k8s/fe`를 별도로 감시)
- 자동 동기화: 활성화

Application이 다른 Git 저장소에서 관리되고 있다면 그 원본 정의를 수정한다.
AppProject의 허용 소스에도 배포 저장소가 포함되어야 한다.
전환 후 Argo CD에서 `Synced`·`Healthy`와 실제 이미지 버전을 확인한다.

## 재시도 및 롤백

- 자동 배포: BE main 병합 → CI 검사 → 이미지 업로드 → `deploy` job이 배포 저장소 갱신
- 매니페스트 반영만 실패: Actions에서 `Re-run failed jobs`로 재시도 (성공한 이미지 빌드는 재실행하지 않음)
- 기존 실패 배포 복구: 수정된 워크플로우가 main에 병합된 후 Actions → `Deploy` → `Run workflow`
  → main 선택 → 이미 업로드된 태그(예: `v0.50.0`) 입력
- 롤백: 같은 수동 실행에서 이전 버전 태그 입력 (이미지를 다시 빌드하지 않음)
- `Re-run all jobs`는 기존 이미지의 중복 검사에 막힐 수 있으므로 사용하지 않는다
- CI 파일만 바뀐 병합은 이미지 빌드를 건너뛴다. 이번 연결 확인은 수동 `Deploy`로 한다
- Git push 성공과 클러스터 배포 완료는 별개다. 최종 성공은 Argo CD의 상태와 서비스 기동으로 확인한다
