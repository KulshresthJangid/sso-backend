#!/bin/bash
set -euo pipefail

# ─── Variables ───────────────────────────────────────────────────────────────
BACKEND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_OUTPUT_DIR="$BACKEND_DIR/deploy"

echo "=========================================================="
echo "          SSO Backend Build & Deploy Script               "
echo "=========================================================="

# 1. Build Backend (Spring Boot)
echo "[1/2] Building Backend (Spring Boot)..."
cd "$BACKEND_DIR"
mvn clean install -DskipTests
BACKEND_JAR=$(ls target/sso-*.jar | head -n 1)
echo "✅ Backend built successfully: $BACKEND_JAR"

# 2. Prepare Deploy Directory
echo "[2/2] Preparing deployment artifacts..."
rm -rf "$BUILD_OUTPUT_DIR"
mkdir -p "$BUILD_OUTPUT_DIR"
cp "$BACKEND_DIR/$BACKEND_JAR" "$BUILD_OUTPUT_DIR/sso-backend.jar"

echo "=========================================================="
echo "✅ Build Complete!"
echo "Artifacts are located in: $BUILD_OUTPUT_DIR"
echo ""
echo "─── Nginx Reverse Proxy Configuration ───"
echo "To route to the backend, add the following to your Nginx configuration:"
echo ""
echo "location /api/ {"
echo "    proxy_pass http://localhost:9000;"
echo "    proxy_set_header Host \$host;"
echo "    proxy_set_header X-Real-IP \$remote_addr;"
echo "    proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;"
echo "    proxy_set_header X-Forwarded-Proto \$scheme;"
echo "}"
echo ""
echo "# Tenant-scoped endpoints for SSO Server"
echo "location ~ ^/([^/]+)/(oauth2|login) {"
echo "    proxy_pass http://localhost:9000;"
echo "    proxy_set_header Host \$host;"
echo "    proxy_set_header X-Real-IP \$remote_addr;"
echo "    proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;"
echo "    proxy_set_header X-Forwarded-Proto \$scheme;"
echo "}"
echo "=========================================================="
