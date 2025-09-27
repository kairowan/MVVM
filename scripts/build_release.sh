#!/usr/bin/env bash
# mvvm 项目打包脚本 (菜单 + 多渠道 + 全局日志 + 自动签名适配)

set -euo pipefail

# --- 绝对路径与目录 ---
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

# --- 核心变量 ---
APP_MODULE="${APP_MODULE:-}"
OUTPUT_DIR="${OUTPUT_DIR:-$PROJECT_ROOT/output}"
GRADLEW="${GRADLEW:-$PROJECT_ROOT/gradlew}"
PROP_FILE="${PROP_FILE:-$PROJECT_ROOT/gradle.properties}"
WALLE_JAR="${WALLE_JAR:-$PROJECT_ROOT/walle-cli-all.jar}"
CHANNEL_FILE="${CHANNEL_FILE:-$PROJECT_ROOT/channel_list.txt}"
CHANNELS="${CHANNELS:-}"

# --- 日志 ---
LOG_FILE="$SCRIPT_DIR/build.log"
: > "$LOG_FILE"
exec > >(tee -a "$LOG_FILE") 2>&1

# --- 工具方法 ---
color() { local c="$1"; shift; printf "\033[%sm%s\033[0m\n" "$c" "$*"; }
info(){ color "1;34" ">>> $*"; }
ok()  { color "1;32" "✅ $*"; }
warn(){ color "1;33" "⚠️  $*"; }
err() { color "1;31" "❌ $*"; }
tolower(){ tr '[:upper:]' '[:lower:]'; }

trap 'rc=$?; err "构建失败（退出码 $rc）"; echo "失败命令: $BASH_COMMAND"; echo "日志: $LOG_FILE"; exit $rc' ERR

# --- 菜单选择 ---
if [ -t 0 ]; then
  echo "请选择打包类型："
  select opt in "APK Release" "APK Debug" "AAB Release" "退出"; do
    case $REPLY in
      1) PACKAGE_TYPE=apk; BUILD_TYPE=Release; break;;
      2) PACKAGE_TYPE=apk; BUILD_TYPE=Debug;  break;;
      3) PACKAGE_TYPE=aab; BUILD_TYPE=Release; break;;
      4) exit;;
      *) warn "无效选择，请重新输入数字" ;;
    esac
  done
else
  PACKAGE_TYPE="${PACKAGE_TYPE:-apk}"
  BUILD_TYPE="${BUILD_TYPE:-Release}"
fi
info "你选择了：$PACKAGE_TYPE $BUILD_TYPE"
BUILD_TYPE_LOWER=$(echo "$BUILD_TYPE" | tolower)

# --- 校验 gradlew ---
[ -x "$GRADLEW" ] || { err "找不到可执行的 $GRADLEW（应在项目根目录）"; exit 1; }

# --- 自动探测 app 模块 ---
detect_app_module() {
  if [ -n "$APP_MODULE" ]; then return; fi
  local hit
  hit=$(find "$PROJECT_ROOT" -maxdepth 2 -type f \( -name "build.gradle" -o -name "build.gradle.kts" \) \
        -print0 | xargs -0 grep -l -E "com\.android\.application" | head -n 1 || true)
  if [ -n "$hit" ]; then
    APP_MODULE=$(dirname "$hit")
    APP_MODULE=${APP_MODULE#"$PROJECT_ROOT/"}
  else
    APP_MODULE="app"
    warn "未探测到应用模块，回退使用默认: $APP_MODULE"
  fi
}
detect_app_module

# --- 读取签名信息 ---
prop() { grep -E "^$1=" "$PROP_FILE" | head -n 1 | cut -d'=' -f2- | tr -d '\r'; }

KEYSTORE_PATH_RAW=$(prop "KEYSTORE_PATH" || true)
KEYSTORE_ALIAS=$(prop "KEYSTORE_ALIAS" || true)
KEYSTORE_PASS=$(prop "KEYSTORE_PASS" || true)
KEY_PASS=$(prop "KEY_PASS" || true)

# 如果 gradle.properties 没有 → 尝试从 build.gradle(.kts) 解析
if [ -z "$KEYSTORE_PATH_RAW" ] || [ -z "$KEYSTORE_ALIAS" ] || [ -z "$KEYSTORE_PASS" ] || [ -z "$KEY_PASS" ]; then
  info "gradle.properties 未配置签名，尝试从 build.gradle(.kts) 读取..."
  BUILD_GRADLE_FILE="$PROJECT_ROOT/$APP_MODULE/build.gradle.kts"
  [ -f "$BUILD_GRADLE_FILE" ] || BUILD_GRADLE_FILE="$PROJECT_ROOT/$APP_MODULE/build.gradle"

  if [ -f "$BUILD_GRADLE_FILE" ]; then
    KEYSTORE_PATH_RAW=$(grep -E 'storeFile\s*=\s*file' "$BUILD_GRADLE_FILE" | sed -E 's/.*file\(["'\'']([^"'\''"]+)["'\'']\).*/\1/' | head -n1)
    KEYSTORE_ALIAS=$(grep -E 'keyAlias\s*=' "$BUILD_GRADLE_FILE" | sed -E 's/.*=\s*["'\'']([^"'\''"]+)["'\''].*/\1/' | head -n1)
    KEYSTORE_PASS=$(grep -E 'storePassword\s*=' "$BUILD_GRADLE_FILE" | sed -E 's/.*=\s*["'\'']([^"'\''"]+)["'\''].*/\1/' | head -n1)
    KEY_PASS=$(grep -E 'keyPassword\s*=' "$BUILD_GRADLE_FILE" | sed -E 's/.*=\s*["'\'']([^"'\''"]+)["'\''].*/\1/' | head -n1)
  fi
fi

# 处理 keystore 路径
if [ -n "${KEYSTORE_PATH_RAW:-}" ]; then
  if [[ "$KEYSTORE_PATH_RAW" = /* ]]; then
    KEYSTORE_PATH="$KEYSTORE_PATH_RAW"
  else
    KEYSTORE_PATH="$PROJECT_ROOT/$APP_MODULE/$KEYSTORE_PATH_RAW"
  fi
fi

# 最终检查
if [ -z "${KEYSTORE_PATH_RAW:-}" ] || [ -z "${KEYSTORE_ALIAS:-}" ] || [ -z "${KEYSTORE_PASS:-}" ] || [ -z "${KEY_PASS:-}" ]; then
  err "未检测到签名信息，请在 gradle.properties 或 $APP_MODULE/build.gradle(.kts) 中配置"
  exit 1
fi

info "签名信息：alias=$KEYSTORE_ALIAS, file=$KEYSTORE_PATH"

# --- 输出目录 ---
mkdir -p "$OUTPUT_DIR"

# --- assemble 任务 ---
TASKS="$("$GRADLEW" ":$APP_MODULE:tasks" --all --console=plain \
  | awk -v bt="$BUILD_TYPE" '($1 ~ /^assemble/ && $1 ~ (bt "$")) {print $1}' \
  | grep -v "AndroidTest" | grep -v "UnitTest" \
  | sort -u || true)"

if [ -n "${CHANNELS:-}" ]; then
  IFS=',' read -r -a CHS <<< "$CHANNELS"
  FILTERED=""
  for t in $TASKS; do
    for ch in "${CHS[@]}"; do
      if echo "$t" | tr '[:upper:]' '[:lower:]' | grep -q "$(echo "$ch" | tolower)"; then
        FILTERED+="$t"$'\n'
        break
      fi
    done
  done
  TASKS=$(echo "$FILTERED" | sort -u)
fi
FLAVOR_TASKS=$(echo "$TASKS" | grep -E "^assemble[A-Z].*${BUILD_TYPE}$" || true)

# --- 清理 ---
info "清理旧构建..."
"$GRADLEW" clean

# --- 构建参数 ---
GRADLE_ARGS=(--no-daemon --parallel --stacktrace
  "-PKEYSTORE_PATH=$KEYSTORE_PATH"
  "-PKEYSTORE_ALIAS=$KEYSTORE_ALIAS"
  "-PKEYSTORE_PASS=$KEYSTORE_PASS"
  "-PKEY_PASS=$KEY_PASS"
)

# --- 构建 ---
if [ -n "$FLAVOR_TASKS" ]; then
  info "检测到多渠道 Variant 任务："; echo "$FLAVOR_TASKS" | sed 's/^/  - /'
  for task in $FLAVOR_TASKS; do
    info "构建 $task ..."
    if [ "$PACKAGE_TYPE" = "apk" ]; then
      "$GRADLEW" ":$APP_MODULE:$task" "${GRADLE_ARGS[@]}"
    else
      btask=$(echo "$task" | sed "s/^assemble/bundle/")
      "$GRADLEW" ":$APP_MODULE:$btask" "${GRADLE_ARGS[@]}"
    fi
  done
else
  info "未检测到多渠道 Variant，构建单一渠道..."
  if [ "$PACKAGE_TYPE" = "apk" ]; then
    "$GRADLEW" ":$APP_MODULE:assemble$BUILD_TYPE" "${GRADLE_ARGS[@]}"
  else
    "$GRADLEW" ":$APP_MODULE:bundle$BUILD_TYPE" "${GRADLE_ARGS[@]}"
  fi
fi

# --- 收集产物 ---
info "收集构建产物到：$OUTPUT_DIR"
if [ "$PACKAGE_TYPE" = "apk" ]; then
  find "$PROJECT_ROOT/$APP_MODULE/build/outputs/apk"    -type f -name "*${BUILD_TYPE_LOWER}.apk"  -print0 2>/dev/null \
    | xargs -0 -I{} cp "{}" "$OUTPUT_DIR/" || true
else
  find "$PROJECT_ROOT/$APP_MODULE/build/outputs/bundle" -type f -name "*${BUILD_TYPE_LOWER}.aab" -print0 2>/dev/null \
    | xargs -0 -I{} cp "{}" "$OUTPUT_DIR/" || true
fi

# --- Walle ---
if [ -z "$FLAVOR_TASKS" ] && [ "$PACKAGE_TYPE" = "apk" ] && [ -f "$WALLE_JAR" ] && [ -f "$CHANNEL_FILE" ]; then
  info "使用 Walle 生成多渠道包..."
  mkdir -p "$OUTPUT_DIR/channels"
  shopt -s nullglob
  for apk in "$OUTPUT_DIR"/*.apk; do
    java -jar "$WALLE_JAR" put -c "$CHANNEL_FILE" "$apk" "$OUTPUT_DIR/channels/"
  done
  shopt -u nullglob
  ok "Walle 渠道包已输出：$OUTPUT_DIR/channels"
fi

ok "所有产物已输出到：$OUTPUT_DIR"
info "构建日志：$LOG_FILE（实时查看：tail -f \"$LOG_FILE\"）"
