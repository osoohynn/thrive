#!/bin/bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
cd "$project_dir"

if ! command -v gh >/dev/null 2>&1; then
    export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
fi
if ! command -v gh >/dev/null 2>&1; then
    echo 'GitHub CLI가 필요합니다. brew install gh 실행 후 다시 실행하세요.' >&2
    exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
    gh auth login --hostname github.com --git-protocol https --web
fi
owner="$(gh api user --jq .login)"
repository="$owner/pleos"

if git remote get-url origin >/dev/null 2>&1; then
    origin="$(git remote get-url origin)"
    case "$origin" in
        "https://github.com/$repository"|"https://github.com/$repository.git"|"git@github.com:$repository.git") ;;
        *)
            echo "기존 origin이 예상 저장소와 다릅니다: $origin" >&2
            echo '기존 연결을 유지했습니다. 원하는 저장소 주소를 먼저 확인하세요.' >&2
            exit 1
            ;;
    esac
else
    # Creating an existing name fails safely instead of pushing into another repo.
    gh repo create "$repository" --private --source "$project_dir" --remote origin
fi

# Use GitHub CLI credentials for this push without changing global Git config.
git -c credential.helper= -c 'credential.helper=!gh auth git-credential' push --set-upstream origin main
printf '\nGitHub 업로드 완료: https://github.com/%s\n' "$repository"
