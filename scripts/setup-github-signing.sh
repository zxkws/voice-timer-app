#!/usr/bin/env bash
set -euo pipefail

REPO="zxkws/voice-timer-app"
CONFIG_DIR="${HOME}/.config/voice-timer-app"

if [[ ! -f "${CONFIG_DIR}/release.jks" || ! -f "${CONFIG_DIR}/store.pass" ]]; then
  echo "Missing local signing files under ${CONFIG_DIR}" >&2
  exit 1
fi

base64 < "${CONFIG_DIR}/release.jks" | gh secret set VOICE_TIMER_KEYSTORE_B64 --repo "${REPO}"
gh secret set VOICE_TIMER_STORE_PASSWORD --repo "${REPO}" < "${CONFIG_DIR}/store.pass"

echo "GitHub signing secrets configured for ${REPO}."
