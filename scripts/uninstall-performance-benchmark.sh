#!/usr/bin/env bash
set -euo pipefail

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command '$1' was not found in PATH." >&2
    exit 1
  fi
}

require_command adb

mapfile -t DEVICES < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ ${#DEVICES[@]} -ne 1 ]]; then
  echo "Expected exactly one connected Android device in 'device' state; found ${#DEVICES[@]}." >&2
  adb devices >&2
  exit 1
fi

SERIAL="${DEVICES[0]}"
MODEL="$(adb -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')"

printf 'EpicHub benchmark uninstall\n'
printf 'Device : %s (%s)\n\n' "$MODEL" "$SERIAL"

uninstall_if_present() {
  local package="$1"
  if adb -s "$SERIAL" shell pm path "$package" >/dev/null 2>&1; then
    echo "Removing $package"
    adb -s "$SERIAL" uninstall "$package"
  else
    echo "$package is not installed"
  fi
}

uninstall_if_present com.epicefi.epichub.benchmark
uninstall_if_present com.epicefi.epichub

echo
echo "EpicHub benchmark packages removed."
