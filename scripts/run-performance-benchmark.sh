#!/usr/bin/env bash
set -euo pipefail

TEST_CLASS="com.epicefi.epichub.benchmark.EpicHubNavigationBenchmark"
METHOD=""
APP_APK=""
BENCHMARK_APK=""
OUTPUT_DIRECTORY=""
ALLOW_EMULATOR=0

usage() {
  cat <<'EOF'
Usage: ./run-performance-benchmark.sh [options]

Options:
  --method NAME          Run one benchmark method only.
                         Examples: warmWorkspaceNavigation,
                         warmDiagnosticsTabs, coldFirstVisitJourney
  --test-class CLASS     Override the instrumentation test class.
  --app APK              Path to EpicHub performance APK.
  --benchmark APK        Path to Macrobenchmark APK.
  --output DIR           Directory for pulled benchmark evidence.
  --allow-emulator       Allow emulator execution (harness debugging only).
  -h, --help             Show this help.

With no APK arguments, the script expects the qualified CI-kit files
EpicHub-Performance.apk and EpicHub-Macrobenchmark.apk beside this script.
EOF
}

while (($#)); do
  case "$1" in
    --method)
      METHOD="${2:?--method requires a value}"
      shift 2
      ;;
    --test-class)
      TEST_CLASS="${2:?--test-class requires a value}"
      shift 2
      ;;
    --app)
      APP_APK="${2:?--app requires a value}"
      shift 2
      ;;
    --benchmark)
      BENCHMARK_APK="${2:?--benchmark requires a value}"
      shift 2
      ;;
    --output)
      OUTPUT_DIRECTORY="${2:?--output requires a value}"
      shift 2
      ;;
    --allow-emulator)
      ALLOW_EMULATOR=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command '$1' was not found in PATH." >&2
    exit 1
  fi
}

require_command adb
require_command sha256sum
require_command zip
require_command find

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

if [[ -z "$APP_APK" && -f "$SCRIPT_DIR/EpicHub-Performance.apk" ]]; then
  APP_APK="$SCRIPT_DIR/EpicHub-Performance.apk"
fi
if [[ -z "$BENCHMARK_APK" && -f "$SCRIPT_DIR/EpicHub-Macrobenchmark.apk" ]]; then
  BENCHMARK_APK="$SCRIPT_DIR/EpicHub-Macrobenchmark.apk"
fi

if [[ -z "$APP_APK" || -z "$BENCHMARK_APK" ]]; then
  echo "Could not locate both qualified APKs." >&2
  echo "Place EpicHub-Performance.apk and EpicHub-Macrobenchmark.apk beside this script," >&2
  echo "or pass --app and --benchmark explicitly." >&2
  exit 1
fi

APP_APK="$(realpath "$APP_APK")"
BENCHMARK_APK="$(realpath "$BENCHMARK_APK")"

mapfile -t DEVICES < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ ${#DEVICES[@]} -ne 1 ]]; then
  echo "Expected exactly one connected Android device in 'device' state; found ${#DEVICES[@]}." >&2
  adb devices >&2
  exit 1
fi

SERIAL="${DEVICES[0]}"
MODEL="$(adb -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')"
API_LEVEL="$(adb -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
FINGERPRINT="$(adb -s "$SERIAL" shell getprop ro.build.fingerprint | tr -d '\r')"
QEMU="$(adb -s "$SERIAL" shell getprop ro.boot.qemu | tr -d '\r')"

if [[ "$QEMU" == "1" && "$ALLOW_EMULATOR" -ne 1 ]]; then
  echo "Refusing to run performance qualification on emulator '$MODEL'." >&2
  echo "Use --allow-emulator only for harness debugging, not performance acceptance." >&2
  exit 1
fi

SELECTOR="$TEST_CLASS"
if [[ -n "$METHOD" ]]; then
  SELECTOR="$TEST_CLASS#$METHOD"
fi

APP_HASH="$(sha256sum "$APP_APK" | awk '{print $1}')"
BENCHMARK_HASH="$(sha256sum "$BENCHMARK_APK" | awk '{print $1}')"

printf 'EpicHub Macrobenchmark\n'
printf 'Device : %s (%s)\n' "$MODEL" "$SERIAL"
printf 'API    : %s\n' "$API_LEVEL"
printf 'Target : %s\n' "$SELECTOR"
printf 'Mode   : qualified prebuilt APKs\n\n'
printf 'App APK       : %s\n' "$APP_APK"
printf 'App SHA-256   : %s\n' "$APP_HASH"
printf 'Benchmark APK : %s\n' "$BENCHMARK_APK"
printf 'Bench SHA-256 : %s\n\n' "$BENCHMARK_HASH"

adb -s "$SERIAL" install -r -t "$APP_APK"
adb -s "$SERIAL" install -r -t "$BENCHMARK_APK"

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
if [[ -z "$OUTPUT_DIRECTORY" ]]; then
  OUTPUT_DIRECTORY="$SCRIPT_DIR/benchmark-results/$TIMESTAMP"
fi
mkdir -p "$OUTPUT_DIRECTORY"
OUTPUT_DIRECTORY="$(cd "$OUTPUT_DIRECTORY" && pwd)"

DEVICE_OUTPUT="/sdcard/Android/media/com.epicefi.epichub.benchmark/epichub-benchmark"
adb -s "$SERIAL" shell rm -rf "$DEVICE_OUTPUT"
adb -s "$SERIAL" shell mkdir -p "$DEVICE_OUTPUT"

cat > "$OUTPUT_DIRECTORY/benchmark-run-manifest.txt" <<EOF
EpicHub physical Macrobenchmark
timestamp=$TIMESTAMP
deviceModel=$MODEL
apiLevel=$API_LEVEL
buildFingerprint=$FINGERPRINT
selector=$SELECTOR
appApkSha256=$APP_HASH
benchmarkApkSha256=$BENCHMARK_HASH
EOF

INSTRUMENTATION="com.epicefi.epichub.benchmark/androidx.test.runner.AndroidJUnitRunner"
LOG_PATH="$OUTPUT_DIRECTORY/instrumentation-output.txt"

echo "Running benchmark. Keep the device connected, awake and unlocked."
echo
set +e
adb -s "$SERIAL" shell am instrument -w -r \
  -e androidx.benchmark.output.enable true \
  -e additionalTestOutputDir "$DEVICE_OUTPUT" \
  -e class "$SELECTOR" \
  "$INSTRUMENTATION" 2>&1 | tee "$LOG_PATH"
INSTRUMENT_EXIT=${PIPESTATUS[0]}
set -e

if [[ $INSTRUMENT_EXIT -ne 0 ]] || grep -Eq 'INSTRUMENTATION_FAILED|FAILURES!!!|Process crashed' "$LOG_PATH"; then
  echo "Macrobenchmark instrumentation failed. See: $LOG_PATH" >&2
  exit 1
fi

PULLED_ROOT="$OUTPUT_DIRECTORY/device-output"
mkdir -p "$PULLED_ROOT"
adb -s "$SERIAL" pull "$DEVICE_OUTPUT" "$PULLED_ROOT"

mapfile -t RESULT_FILES < <(
  find "$OUTPUT_DIRECTORY" -type f \
    \( -name '*benchmarkData.json' -o -name '*.trace' -o -name '*.perfetto-trace' \) \
    -print
)

if [[ ${#RESULT_FILES[@]} -eq 0 ]]; then
  echo "Benchmark finished but no JSON/Perfetto result files were pulled from $DEVICE_OUTPUT." >&2
  exit 1
fi

ZIP_PATH="$SCRIPT_DIR/EpicHub-Benchmark-$TIMESTAMP.zip"
rm -f "$ZIP_PATH"
(
  cd "$OUTPUT_DIRECTORY"
  zip -qr "$ZIP_PATH" .
)

echo
echo "Benchmark completed successfully."
echo "Result files:"
printf '  %s\n' "${RESULT_FILES[@]}"
echo
echo "Upload this ZIP for analysis:"
echo "$ZIP_PATH"
