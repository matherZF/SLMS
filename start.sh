#!/usr/bin/env bash
# ============================================================================
# 简易 WMS 系统 —— 一键启动/构建脚本
# 用法：
#   ./start.sh build   —— 仅构建：后端 mvn package + 前端 npm install + build
#   ./start.sh dev     —— 开发模式：启动后端 jar（无则先构建）+ 前端 vite dev (5173)
#   ./start.sh stop    —— 停止本机启动的后端 java 进程 与 vite dev 进程（按端口匹配）
# ============================================================================
set -u
ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
JAR_GLOB="$BACKEND_DIR/target/wms-backend-*.jar"
BACKEND_PORT=8080
FRONTEND_PORT=5173

log()  { printf "\033[36m[WMS]\033[0m %s\n" "$*"; }
warn() { printf "\033[33m[WARN]\033[0m %s\n" "$*"; }
err()  { printf "\033[31m[ERR]\033[0m %s\n" "$*"; exit 1; }

cmd_exists() { command -v "$1" >/dev/null 2>&1; }

require_env() {
  cmd_exists java || err "请先安装 JDK 17+ (java 未找到)"
  cmd_exists mvn  || warn "mvn 未找到，若需从头构建 jar 请先安装 Maven 3.8+"
  cmd_exists node || warn "node 未找到，若需安装前端依赖或构建请先安装 Node 18+"
  cmd_exists npm  || warn "npm 未找到，请安装 Node 后重试"
}

wait_for_port() {
  local port=$1 timeout=${2:-30} i=0
  while [ $i -lt $timeout ]; do
    if (echo > /dev/tcp/127.0.0.1/$port) 2>/dev/null; then
      return 0
    fi
    sleep 1; i=$((i+1))
  done
  return 1
}

kill_port() {
  local port=$1
  local pids
  if cmd_exists lsof; then
    pids=$(lsof -ti :$port 2>/dev/null || true)
  elif cmd_exists ss; then
    pids=$(ss -ltnp 2>/dev/null | awk -v p=":$port" '$4 ~ p {print $0}' | grep -oE 'pid=[0-9]+' | head -n 1 | cut -d= -f2)
  fi
  if [ -n "${pids:-}" ]; then
    log "停止端口 $port 关联进程: $pids"
    kill -9 $pids 2>/dev/null || true
  fi
}

# ---------- 构建 ----------
do_build_backend() {
  log "构建后端 jar ..."
  cd "$BACKEND_DIR"
  # 若已有 jar 且 pom.xml 不新于 jar，则跳过（但为保证一致默认构建）
  mvn clean package -DskipTests
  [ $? -eq 0 ] || err "后端构建失败"
  local jar=$(ls $JAR_GLOB 2>/dev/null | head -n1)
  [ -n "$jar" ] || err "构建后未找到 jar: $JAR_GLOB"
  log "后端 jar 构建完成：$jar"
}

do_build_frontend() {
  log "安装/构建前端 ..."
  cd "$FRONTEND_DIR"
  if [ ! -d node_modules ]; then
    npm install --legacy-peer-deps --registry=https://registry.npmmirror.com || err "前端依赖安装失败"
  fi
  npm run build || err "前端构建失败"
  log "前端构建完成，产物：$FRONTEND_DIR/dist/"
}

# ---------- 启动 ----------
start_backend() {
  local jar=$(ls $JAR_GLOB 2>/dev/null | head -n1)
  if [ -z "$jar" ]; then
    warn "后端 jar 不存在，执行构建..."
    do_build_backend
    jar=$(ls $JAR_GLOB 2>/dev/null | head -n1)
  fi
  log "清空旧 H2 数据？（y/N，默认否）："
  read -t 5 -p "> " ans || ans="N"
  case "$ans" in y|Y|yes|YES)
    log "清理 $BACKEND_DIR/data/ ..."
    rm -rf "$BACKEND_DIR/data" ;;
  esac
  mkdir -p "$BACKEND_DIR/data"
  kill_port $BACKEND_PORT
  if [ -z "${JWT_SECRET:-}" ]; then
    JWT_SECRET="$( (head -c 32 /dev/urandom 2>/dev/null || date +%s%N) | base64 | tr -d '\n')"
    export JWT_SECRET
    warn "未设置 JWT_SECRET，已为本次启动生成临时密钥（重启后已有 token 失效）"
  fi
  log "启动后端：java -jar $jar (端口 $BACKEND_PORT)"
  cd "$BACKEND_DIR"
  nohup java -jar "$jar" > "$BACKEND_DIR/app.log" 2>&1 &
  BACKEND_PID=$!
  log "后端 PID=$BACKEND_PID  日志: $BACKEND_DIR/app.log"
  if wait_for_port $BACKEND_PORT 40; then
    log "✅ 后端启动成功  http://localhost:$BACKEND_PORT/api/products"
  else
    warn "后端端口 $BACKEND_PORT 仍未就绪，请查看 $BACKEND_DIR/app.log"
  fi
}

start_frontend_dev() {
  kill_port $FRONTEND_PORT
  log "启动前端 Vite dev 端口 $FRONTEND_PORT ..."
  cd "$FRONTEND_DIR"
  if [ ! -d node_modules ]; then
    log "首次运行，安装前端依赖..."
    npm install --legacy-peer-deps --registry=https://registry.npmmirror.com || err "前端依赖安装失败"
  fi
  nohup npm run dev -- --host 0.0.0.0 --port $FRONTEND_PORT > "$FRONTEND_DIR/dev.log" 2>&1 &
  FRONT_PID=$!
  log "前端 PID=$FRONT_PID  日志: $FRONTEND_DIR/dev.log"
  if wait_for_port $FRONTEND_PORT 20; then
    log "✅ 前端启动成功  http://localhost:$FRONTEND_PORT   (/api 代理到后端 8080)"
  else
    warn "前端端口 $FRONTEND_PORT 未就绪，请查看 $FRONTEND_DIR/dev.log"
  fi
}

# ---------- main ----------
require_env
ACTION="${1:-}"
case "$ACTION" in
  build)
    do_build_backend
    do_build_frontend
    log "🎉 全部构建完成：后端 jar + 前端 dist/"
    ;;
  dev)
    start_backend
    start_frontend_dev
    echo ""
    log "================================================================="
    log "  访问前端入口  : http://localhost:$FRONTEND_PORT"
    log "  后端健康检查  : http://localhost:$BACKEND_PORT/api/products"
    log "  停止          : ./start.sh stop"
    log "================================================================="
    ;;
  stop)
    kill_port $BACKEND_PORT
    kill_port $FRONTEND_PORT
    log "✅ 已尝试清理端口 $BACKEND_PORT / $FRONTEND_PORT 的进程"
    ;;
  *)
    cat <<EOF
用法：$0 <build|dev|stop>
  build   仅构建：后端 mvn package + 前端 npm build
  dev     开发模式：启动后端 jar + 前端 vite dev（热更新）
  stop    按端口停后端与前端进程
EOF
    exit 1
    ;;
esac
