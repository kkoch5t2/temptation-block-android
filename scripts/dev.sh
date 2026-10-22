#!/usr/bin/env bash
set -euo pipefail
task_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$task_root"
case "${1:-help}" in
  test-android)
    python3 scripts/test_core_rules.py
    python3 scripts/test_group_rules.py
    python3 scripts/test_duration_format.py
    python3 scripts/test_browser_navigation.py
    python3 scripts/test_app_surface_rules.py
    ;;
  build-android)
    task_args=()
    if [[ "${GRADLE_OFFLINE:-false}" == true ]]; then task_args+=(--offline); fi
    gradle assembleDebug lintDebug --no-daemon --max-workers=2 "${task_args[@]}"
    ;;
  *)
    printf 'Usage: bash scripts/dev.sh {test-android|build-android}\n'
    exit 2
    ;;
esac
