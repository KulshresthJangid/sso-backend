#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# SSO Backend — Standalone Deployment Script
#
# Runs ON the server in the project directory. Builds the Spring Boot JAR
# with Maven, installs/reloads the systemd service, and restarts it.
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
# Nginx blocks needed in /etc/nginx/conf.d/services.conf:
#   (see nginx_blocks.conf in this directory for the full snippet)
#
# Service managed:
#   sso-backend  — Spring Authorization Server on port 9000
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"
MODE="${1:-}"
SERVICE_NAME="sso-backend"

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

  [ -f "$ENV_FILE" ] || die ".env not found — cannot get DB credentials."
  set -o allexport; source "$ENV_FILE"; set +o allexport

  log "Stopping $SERVICE_NAME..."
  systemctl stop "$SERVICE_NAME" 2>/dev/null || true

  log "Dropping and recreating sso_db..."
  DB_NAME="${DB_NAME:-sso_db}"
  DB_USER="${DB_USER:-sso_user}"
  sudo -u postgres psql -c "DROP DATABASE IF EXISTS ${DB_NAME};" \
    || die "Failed to drop DB. Is PostgreSQL running?"
  sudo -u postgres psql -c "CREATE DATABASE ${DB_NAME} OWNER ${DB_USER};" \
    || die "Failed to recreate DB."
  ok "Database wiped and recreated."

  log "Removing build artifacts..."
  rm -rf "$SCRIPT_DIR/target"
  ok "Build artifacts removed. Proceeding with fresh install..."
  echo ""
fi

# ── Pre-flight checks ─────────────────────────────────────────────────────────
log "Checking prerequisites..."
command -v java   >/dev/null || die "Java not installed. Install Java 21 JDK."
command -v mvn    >/dev/null || die "Maven not installed. Run: apt install maven / yum install maven"
[ -f "$ENV_FILE" ] || die ".env not found. Copy .env.example → .env and fill in secrets."

JAVA_VER=$(java -version 2>&1 | head -1 | grep -oP '(?<=")\d+' | head -1)
[[ "$JAVA_VER" -ge 21 ]] || die "Java 21+ required. Found: $JAVA_VER"
ok "Prerequisites OK (Java $JAVA_VER)."

# ── Pull latest code ──────────────────────────────────────────────────────────
log "Pulling latest code..."
cd "$SCRIPT_DIR"
git pull origin main

# ── Build ─────────────────────────────────────────────────────────────────────
log "Building JAR (skipping tests)..."
mvn clean package -DskipTests -q 2>&1 | tail -5
JAR=$(ls "$SCRIPT_DIR"/target/sso-*.jar 2>/dev/null | grep -v 'original' | head -1)
[ -f "$JAR" ] || die "JAR not found after build. Check maven output."
ok "Built: $JAR"

# ── Install / update systemd service ─────────────────────────────────────────
log "Installing systemd service..."

# Load env to get key store path
set -o allexport; source "$ENV_FILE"; set +o allexport
KEY_STORE_PATH="${KEY_STORE_PATH:-${HOME}/.vault-sso/signing}"
mkdir -p "$(dirname "$KEY_STORE_PATH")"

cat > "/etc/systemd/system/${SERVICE_NAME}.service" <<EOF
[Unit]
Description=Vault SSO Authorization Server
After=network.target postgresql.service
Wants=postgresql.service

[Service]
Type=simple
User=root
WorkingDirectory=${SCRIPT_DIR}
EnvironmentFile=${ENV_FILE}
ExecStart=java -jar ${JAR}
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=${SERVICE_NAME}

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable "$SERVICE_NAME"
ok "systemd service installed."

# ── Restart service ───────────────────────────────────────────────────────────
log "Restarting $SERVICE_NAME..."
systemctl restart "$SERVICE_NAME"

# ── Health check ──────────────────────────────────────────────────────────────
log "Waiting for SSO backend on port 9000 (up to 60s)..."
DEADLINE=$((SECONDS + 60))
while true; do
  STATUS=$(curl -o /dev/null -s -w "%{http_code}" http://localhost:9000/api/health 2>/dev/null \
    || curl -o /dev/null -s -w "%{http_code}" http://localhost:9000/api/orgs 2>/dev/null || true)
  [[ "$STATUS" =~ ^(200|401|403|404)$ ]] && break
  [[ $SECONDS -ge $DEADLINE ]] && { warn "SSO backend did not respond in time. Check: journalctl -u sso-backend -n 50"; break; }
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
log "  Logs         : journalctl -u sso-backend -f"
echo ""
