#!/usr/bin/env bash
# ============================================================
# 智能体运行平台 - 统一构建/启动/验证脚本 (Git Bash / Linux / macOS)
#
# 解决 P1-5: `mvnw package -pl admin-shell -am` 会引用本地仓库旧模块 jar，
# 导致"改了没生效"假象。统一使用 `clean install` 确保嵌套 jar 用上新模块类。
#
# 用法:
#   ./scripts/build.sh install   # 全量构建 + 安装到本地仓库（默认）
#   ./scripts/build.sh start     # 构建后启动后端(9900)
#   ./scripts/build.sh check     # 健康检查（后端是否在线）
#   ./scripts/build.sh all       # install + start + check
# ============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${PORT:-9900}"
JAR="$ROOT/admin-shell/target/admin-shell-1.0.0.jar"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"

build() {
  echo "==> [1/2] clean install admin-shell（含全部依赖模块）"
  (cd "$ROOT" && bash mvnw clean install -pl admin-shell -am -Dmaven.test.skip=true -DskipTests -q)
  echo "==> [2/2] 构建产物: $JAR"
}

start() {
  echo "==> 启动后端 :$PORT（RocksDB: data/rocksdb3）"
  "$JAVA_BIN" -jar "$JAR" --server.port="$PORT" --framework.storage.rocksdb.path=data/rocksdb3
}

check() {
  # Git Bash 下 Windows curl 写 /dev/null 会失败（退出码非 0），
  # 因此不追加 || echo 000，仅以 -w 输出的 HTTP code 判断
  local code
  code=$(curl -s -m 5 -o /dev/null -w "%{http_code}" "http://localhost:$PORT/api/plugins" 2>/dev/null) || true
  if [ "$code" = "200" ]; then
    echo "OK  后端在线 :$PORT"
  else
    echo "FAIL 后端未就绪 :$PORT (HTTP ${code:-timeout})"; exit 1
  fi
}

case "${1:-install}" in
  install) build ;;
  start)   start ;;
  check)   check ;;
  all)     build && start ;;
  *) echo "用法: $0 {install|start|check|all}"; exit 1 ;;
esac
