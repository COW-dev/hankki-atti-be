#!/usr/bin/env bash
set -e

echo "Git hook 설정을 시작합니다..."

git config core.hooksPath .githooks
chmod +x .githooks/*

echo "완료: .githooks 디렉토리를 Git hook 경로로 설정했습니다."
echo ""
echo "설정된 hook 목록:"
for hook in .githooks/*; do
    echo "  - $(basename "$hook")"
done
echo ""
echo "pre-commit: main 직접 커밋 차단 + 시크릿 스캔 활성화됨"
echo "gitleaks 설치 여부: $(command -v gitleaks &>/dev/null && echo '확인됨' || echo '미설치 (grep 폴백 사용, brew install gitleaks 권장)')"
echo "prepare-commit-msg: Claude CLI가 있으면 커밋 메시지 자동 생성 ($(command -v claude &>/dev/null && echo '확인됨' || echo 'Claude CLI 미설치 — 조용히 스킵됨'))"
