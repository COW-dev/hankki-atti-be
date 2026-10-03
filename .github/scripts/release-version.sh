#!/usr/bin/env bash
# main 병합 시 이미지 배포 여부와 버전을 결정한다 (.github/workflows/ci.yml의 version job에서 사용)
# 출력(GITHUB_OUTPUT): release=true|false, version=vX.Y.Z, new_tag=true|false
set -euo pipefail

# 이미지 내용에 영향을 주는 경로. 여기 없는 파일(문서, CI, 에이전트 설정)만 바뀐 병합은 배포하지 않는다
IMAGE_PATHS=(src/main build.gradle settings.gradle gradle gradlew Dockerfile .dockerignore)
SEMVER='^v[0-9]+\.[0-9]+\.[0-9]+$'
out="${GITHUB_OUTPUT:-/dev/stdout}"

highest_version() {
    git tag --list 'v*' "$@" --sort=-v:refname | grep -E "$SEMVER" | head -n 1 || true
}

# 재실행: 이 커밋에 이미 버전이 붙어 있으면 같은 버전으로 다시 배포한다
head_tag=$(highest_version --points-at HEAD)
if [ -n "$head_tag" ]; then
    echo "HEAD에 이미 $head_tag 태그가 있어 같은 버전으로 배포한다" >&2
    printf 'release=true\nversion=%s\nnew_tag=false\n' "$head_tag" >> "$out"
    exit 0
fi

latest=$(highest_version)
if [ -z "$latest" ]; then
    version=v0.1.0
elif git diff --quiet "$latest" HEAD -- "${IMAGE_PATHS[@]}"; then
    echo "$latest 이후 이미지에 영향을 주는 변경이 없어 배포하지 않는다" >&2
    echo "release=false" >> "$out"
    exit 0
else
    # 마이너는 상한 없이 증가한다 (v0.9.0 → v0.10.0). 메이저는 사람이 직접 태그를 찍어 올린다
    IFS=. read -r major minor _ <<< "${latest#v}"
    version="v${major}.$((minor + 1)).0"
fi

echo "새 버전: $version (이전: ${latest:-없음})" >&2
printf 'release=true\nversion=%s\nnew_tag=true\n' "$version" >> "$out"
