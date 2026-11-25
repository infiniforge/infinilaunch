#!/usr/bin/env sh
set -eu
cd -- "$(dirname -- "$0")"

if [ -z "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ] && [ ! -f local.properties ]; then
    for LAUNCH_SDK in "$HOME/android-sdk" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do
        if [ -d "$LAUNCH_SDK/platforms/android-36" ]; then
            export ANDROID_HOME="$LAUNCH_SDK"
            break
        fi
    done
fi

if [ -n "${TERMUX_VERSION:-}" ]; then
    LAUNCH_AAPT2=$(command -v aapt2 || true)
    if [ -z "$LAUNCH_AAPT2" ]; then
        printf '%s\n' 'Install Termux aapt2 before building InfiniLaunch.' >&2
        exit 1
    fi
    set -- "-Pandroid.aapt2FromMavenOverride=$LAUNCH_AAPT2" "$@"
fi

python3 scripts/fetch-dependencies.py
exec ./gradlew :app:assembleDebug "$@"
