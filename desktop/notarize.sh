#!/usr/bin/env bash
# 아침보드 macOS 공증(notarization) 스크립트
#
# 사전 준비 (최초 1회): Apple ID 앱 암호를 키체인에 저장
#   https://appleid.apple.com → 로그인 및 보안 → 앱 암호 에서 암호 발급 후:
#
#   xcrun notarytool store-credentials "morningboard" \
#     --apple-id "<your-apple-id@email>" \
#     --team-id 469F7JUL9Q \
#     --password "<앱-암호-xxxx-xxxx-xxxx-xxxx>"
#
# 그 다음 이 스크립트 실행:
#   ./notarize.sh
#
# DMG 는 이미 Developer ID 로 서명 + 하드닝 런타임이 적용된 상태로 빌드된다
# (build.gradle.kts 의 macOS.signing 블록). 여기서는 공증 제출 → staple 만 한다.

set -euo pipefail

PROFILE="${NOTARY_PROFILE:-morningboard}"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"

cd "$(dirname "$0")"

echo "▶ 1/3  서명된 DMG 빌드…"
./gradlew --no-daemon packageDmg --console=plain

DMG=$(ls -t build/compose/binaries/main/dmg/*.dmg | head -1)
echo "   → $DMG"

echo "▶ 2/3  Apple 공증 제출(완료까지 대기)…"
xcrun notarytool submit "$DMG" --keychain-profile "$PROFILE" --wait

echo "▶ 3/3  staple(공증 티켓 첨부)…"
xcrun stapler staple "$DMG"
xcrun stapler validate "$DMG"

echo "✅ 완료:  $DMG"
echo "   이제 이 DMG 를 받는 사람은 '확인되지 않은 개발자' 경고 없이 바로 열 수 있어요."
