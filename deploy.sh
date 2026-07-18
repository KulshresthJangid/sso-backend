#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# SSO Backend — Standalone Deployment Script
#
# Runs ON the server in the project directory. Builds the Spring Boot JAR
# with Maven, then starts it as a background process via nohup.
# No systemd — process is tracked via a PID file.
#
# Usage:
#   ./deploy.sh                  — rolling update (pull → build → restart)
#   ./deploy.sh fresh-install    — drop PostgreSQL database, wipe build, redeploy
#
# Prerequisites (on the server):
#   • Java 21 JDK + Maven 3.x   — to build and run the JAR
#   • PostgreSQL                 — running as a system service
#   • Git
#   • .env file in this directory (copy from .env.example and fill in secrets)
#
# Logs:   /var/log/sso/sso-backend.log
# PID:    /var/run/sso/sso-backend.pid
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"
MODE="${1:-}"
SERVICE_NAME="sso-backend"
PID_DIR="/var/run/sso"
LOG_DIR="/var/log/sso"
PID_FILE="$PID_DIR/${SERVICE_NAME}.pid"
LOG_FILE="$LOG_DIR/${SERVICE_NAME}.log"

RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

log()  { echo -e "${CYAN}[$(date '+%H:%M:%S')]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
ok()   { echo -e "${GREEN}[OK]${NC} $*"; }
die()  { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# ── Safe .env loader ──────────────────────────────────────────────────────────
load_env() {
  [ -f "$ENV_FILE" ] || die ".env not found. Copy .env.example → .env and fill in secrets."
  while IFS= read -r line; do
    [[ "$line" =~ ^[[:space:]]*# ]] && continue
    [[ -z "${line// }" ]] && continue
    key="${line%%=*}"
    value="${line#*=}"
    key="${key// /}"
    [[ -z "$key" ]] && continue
    value="${value%\"}" ; value="${value#\"}"
    export "$key=$value"
  done < "$ENV_FILE"
}

# ── Process management ────────────────────────────────────────────────────────
stop_service() {
  if [ -f "$PID_FILE" ]; then
    local PID
    PID=$(cat "$PID_FILE" 2>/dev/null || true)
    if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
      log "Stopping $SERVICE_NAME (PID $PID)..."
      kill "$PID" 2>/dev/null || true
      local i=0
      while kill -0 "$PID" 2>/dev/null && [ $i -lt 15 ]; do
        sleep 1; i=$((i+1))
      done
      kill -9 "$PID" 2>/dev/null || true
    fi
    rm -f "$PID_FILE"
  fi
  pkill -f "java -jar .*/target/sso-.*\.jar" 2>/dev/null || true
  ok "$SERVICE_NAME stopped."
}

start_service() {
  local JAR="$1"
  mkdir -p "$PID_DIR" "$LOG_DIR"

  # Rotate log when it exceeds 5 MB
  if [ -f "$LOG_FILE" ] && [ "$(stat -c%s "$LOG_FILE" 2>/dev/null || echo 0)" -gt 5242880 ]; then
    mv "$LOG_FILE" "${LOG_FILE}.old"
  fi

  log "Starting $SERVICE_NAME..."
  nohup java -jar "$JAR" >> "$LOG_FILE" 2>&1 &
  local PID=$!
  echo "$PID" > "$PID_FILE"

  sleep 5
  if ! kill -0 "$PID" 2>/dev/null; then
    echo ""
    echo -e "${RED}[ERROR]${NC} $SERVICE_NAME crashed on startup. Last 40 lines of log:"
    tail -40 "$LOG_FILE" | sed 's/^/  /'
    exit 1
  fi
  ok "$SERVICE_NAME is running (PID $PID). Log: $LOG_FILE"
}

# ── fresh-install: triple confirmation ───────────────────────────────────────
if [[ "$MODE" == "fresh-install" ]]; then
  echo ""
  echo -e "${RED}${BOLD}╔══════════════════════════════════════════════════════════════╗${NC}"
  echo -e "${RED}${BOLD}║            ⚠  FRESH SSO BACKEND INSTALL WARNING  ⚠           ║${NC}"
  echo -e "${RED}${BOLD}╠══════════════════════════════════════════════════════════════╣${NC}"
  echo -e "${RED}${BOLD}║  This will PERMANENTLY DELETE:                               ║${NC}"
  echo -e "${RED}${BOLD}║    • The 'sso_db' PostgreSQL database                        ║${NC}"
  echo -e "${RED}${BOLD}║      (all orgs, users, clients, tokens — gone forever)       ║${NC}"
  echo -e "${RED}${BOLD}║    • Maven build artifacts (target/)                         ║${NC}"
  echo -e "${RED}${BOLD}║                                                              ║${NC}"
  echo -e "${RED}${BOLD}║  There is NO undo.                                           ║${NC}"
  echo -e "${RED}${BOLD}╚══════════════════════════════════════════════════════════════╝${NC}"
  echo ""

  for i in 1 2 3; do
    case $i in
      1) MSG="Are you absolutely sure you want to wipe SSO data? [yes/no]: " ;;
      2) MSG="[Confirmation $i/3] All orgs and users will be deleted. Continue? [yes/no]: " ;;
      3) MSG="[Confirmation 3/3] LAST CHANCE — type 'yes' to confirm wipe: " ;;
    esac
    read -rp "$(echo -e "${RED}${BOLD}$MSG${NC}")" ANSWER
    [[ "$ANSWER" == "yes" ]] || { echo "Aborted. Nothing changed."; exit 0; }
  done

  load_env

  log "Stopping $SERVICE_NAME..."
  stop_service

  log "Dropping and recreating ${DB_NAME:-sso_db}..."
  docker exec session_logger_db psql -U sessionuser -d postgres \
    -c "DROP DATABASE IF EXISTS ${DB_NAME:-sso_db};" \
    || die "Failed to drop DB. Is the session_logger_db container running?"
  docker exec session_logger_db psql -U sessionuser -d postgres \
    -c "CREATE DATABASE ${DB_NAME:-sso_db} OWNER ${DB_USER:-sso_user};" \
    || die "Failed to recreate DB."
  ok "Database wiped and recreated."

  log "Removing build artifacts..."
  rm -rf "$SCRIPT_DIR/target"
  ok "Build artifacts removed. Proceeding with fresh install..."
  echo ""
fi

# ── Pre-flight checks ─────────────────────────────────────────────────────────
log "Checking prerequisites..."
command -v java >/dev/null || die "Java not installed. Install Java 21 JDK."
command -v mvn  >/dev/null || die "Maven not installed. Run: yum install maven"
[ -f "$ENV_FILE" ] || die ".env not found. Copy .env.example → .env and fill in secrets."

JAVA_VER=$(java -version 2>&1 | head -1 | grep -oP '(?<=")\d+' | head -1)
[[ "$JAVA_VER" -ge 21 ]] || die "Java 21+ required. Found: $JAVA_VER"
ok "Prerequisites OK (Java $JAVA_VER)."

# ── Pull latest code ──────────────────────────────────────────────────────────
log "Pulling latest code..."
cd "$SCRIPT_DIR"
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
git pull origin "$CURRENT_BRANCH"

# Re-exec with the freshly pulled script so in-flight edits take effect.
if [[ "${_SSO_PULLED:-0}" != "1" ]]; then
  export _SSO_PULLED=1
  exec "$0" "$@"
fi

# ── Load environment ──────────────────────────────────────────────────────────
load_env
ok "Environment loaded from .env."

# ── Build ─────────────────────────────────────────────────────────────────────
log "Building JAR (skipping tests)..."
BUILD_LOG="$SCRIPT_DIR/build.log"
if ! mvn clean package -DskipTests 2>&1 | tee "$BUILD_LOG" | grep -E '^\[ERROR\]|BUILD SUCCESS|BUILD FAILURE'; then
  die "Maven build failed. Check $BUILD_LOG for details."
fi
grep -q "BUILD SUCCESS" "$BUILD_LOG" || die "Maven reported BUILD FAILURE. Check $BUILD_LOG."

JAR=$(ls "$SCRIPT_DIR"/target/sso-*.jar 2>/dev/null | grep -v 'original' | head -1)
[ -f "$JAR" ] || die "JAR not found after build. Check $BUILD_LOG."
ok "Built: $JAR"

# ── Stop existing process ─────────────────────────────────────────────────────
stop_service

# ── Start service ─────────────────────────────────────────────────────────────
start_service "$JAR"

# ── Health check ──────────────────────────────────────────────────────────────
log "Waiting for SSO backend on port 9000 (up to 60s)..."
DEADLINE=$((SECONDS + 60))
while true; do
  STATUS=$(curl -o /dev/null -s -w "%{http_code}" http://localhost:9000/api/health 2>/dev/null \
    || curl -o /dev/null -s -w "%{http_code}" http://localhost:9000/api/orgs 2>/dev/null || true)
  [[ "$STATUS" =~ ^(200|401|403|404)$ ]] && break
  if [[ $SECONDS -ge $DEADLINE ]]; then
    warn "SSO backend did not respond in time."
    warn "  Check logs: tail -f $LOG_FILE"
    break
  fi
  echo "  waiting... (HTTP ${STATUS:----})"
  sleep 5
done
ok "SSO backend is up."

# ── Reload Nginx ──────────────────────────────────────────────────────────────
if command -v nginx >/dev/null 2>&1; then
  log "Reloading Nginx..."
  nginx -t && systemctl reload nginx && ok "Nginx reloaded." || warn "Nginx reload failed."
fi

echo ""
echo -e "${GREEN}${BOLD}════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}${BOLD}  SSO Backend deploy complete${NC}  [mode: ${MODE:-rolling}]"
echo -e "${GREEN}${BOLD}════════════════════════════════════════════════════════${NC}"
log "  SSO Backend  : http://localhost:9000"
log "  Public path  : http://buildwithkulshresth.com/sso-server/"
log "  OAuth2 flows : http://buildwithkulshresth.com/{tenant}/oauth2/authorize"
log "  Logs         : tail -f $LOG_FILE"
echo ""
