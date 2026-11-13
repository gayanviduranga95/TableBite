#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
MAVEN_VERSION="3.9.11"
MAVEN_HOME="$ROOT_DIR/.tools/apache-maven-$MAVEN_VERSION"
MAVEN_ARCHIVE="$ROOT_DIR/.tools/apache-maven-$MAVEN_VERSION-bin.zip"
MAVEN_URL="https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.zip"

if ! command -v mvn >/dev/null 2>&1 && [[ -f "$MAVEN_HOME/bin/mvn.cmd" ]]; then
  export PATH="$MAVEN_HOME/bin:$PATH"
fi

if ! command -v mvn >/dev/null 2>&1; then
  # Prefer a user-local install so no administrator prompt is required.
  if [[ ! -f "$MAVEN_HOME/bin/mvn.cmd" ]]; then
    echo "Installing Maven locally under .tools..."
    mkdir -p "$ROOT_DIR/.tools"
    if curl.exe -fL --retry 2 -o "$MAVEN_ARCHIVE" "$MAVEN_URL"; then
      ARCHIVE_WIN="$(cygpath -w "$MAVEN_ARCHIVE")"
      TOOLS_WIN="$(cygpath -w "$ROOT_DIR/.tools")"
      powershell.exe -NoProfile -Command \
        "Expand-Archive -LiteralPath '$ARCHIVE_WIN' -DestinationPath '$TOOLS_WIN' -Force"
    fi
  fi
  if [[ -f "$MAVEN_HOME/bin/mvn.cmd" ]]; then
    export PATH="$MAVEN_HOME/bin:$PATH"
  fi
fi

if ! command -v mvn >/dev/null 2>&1; then
  if command -v winget.exe >/dev/null 2>&1; then
    echo "Maven was not found. Installing Apache Maven with winget..."
    winget.exe install --name Maven --exact \
      --accept-source-agreements --accept-package-agreements || true
  fi
fi

if ! command -v mvn >/dev/null 2>&1 && command -v choco.exe >/dev/null 2>&1; then
  echo "Installing Maven with Chocolatey..."
  choco.exe install maven -y --no-progress || true
fi

if ! command -v mvn >/dev/null 2>&1; then
  for candidate in \
    "/c/Program Files/Apache/Maven/bin" \
    "/c/Program Files/Apache/maven/bin" \
    "/c/ProgramData/chocolatey/bin"; do
    if [[ -f "$candidate/mvn.cmd" ]]; then
      export PATH="$candidate:$PATH"
      break
    fi
  done
fi

if ! command -v mvn >/dev/null 2>&1; then
  # Git Bash may not see a newly installed executable until PATH is refreshed.
  if [[ -f "/c/ProgramData/chocolatey/bin/mvn.cmd" ]]; then
    export PATH="/c/ProgramData/chocolatey/bin:$PATH"
  fi
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "Error: Maven could not be installed automatically." >&2
  echo "Install Apache Maven or check your network connection, then run ./run.sh again." >&2
  exit 1
fi

if ! command -v npm >/dev/null 2>&1; then
  echo "Error: Node.js/npm is required." >&2
  exit 1
fi

export DB_USER="${DB_USER:-qr_attendance_app}"
if [[ -z "${DB_PASSWORD:-}" ]]; then
  read -r -s -p "PostgreSQL password for ${DB_USER}: " DB_PASSWORD
  echo
  if [[ -z "$DB_PASSWORD" ]]; then
    echo "Error: PostgreSQL password is required." >&2
    exit 1
  fi
  export DB_PASSWORD
fi

# Code the kitchen types on /kitchen (default: kitchen123 - set your own).
export KITCHEN_ACCESS_CODE="${KITCHEN_ACCESS_CODE:-kitchen123}"

# The backend needs JDK 21+. Maven uses JAVA_HOME, which on some machines still
# points at an old JDK 8, so pick a suitable JDK automatically when needed.
java_major() {
  "$1/bin/java" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*//'
}
if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" || "$(java_major "$JAVA_HOME")" -lt 21 ]]; then
  for candidate in "/c/Program Files/Eclipse Adoptium"/jdk-2* "/c/Program Files/Java"/jdk-2* "/c/Program Files/Microsoft"/jdk-2* /usr/lib/jvm/*-2*; do
    if [[ -x "$candidate/bin/java" && "$(java_major "$candidate")" -ge 21 ]]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi
if [[ -z "${JAVA_HOME:-}" || "$(java_major "$JAVA_HOME")" -lt 21 ]]; then
  echo "Error: JDK 21 or newer is required. Install it and set JAVA_HOME." >&2
  exit 1
fi
echo "Using Java from $JAVA_HOME"

backend_pid=""
frontend_pid=""

cleanup() {
  trap - EXIT INT TERM
  if [[ -n "$frontend_pid" ]] && kill -0 "$frontend_pid" 2>/dev/null; then
    kill "$frontend_pid" 2>/dev/null || true
  fi
  if [[ -n "$backend_pid" ]] && kill -0 "$backend_pid" 2>/dev/null; then
    kill "$backend_pid" 2>/dev/null || true
  fi
  wait "$frontend_pid" "$backend_pid" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "Starting backend (HTTP ${HTTP_PORT:-8080}, WebSocket ${WEBSOCKET_PORT:-8081}, TCP ${TCP_PORT:-5000})..."
(
  cd "$BACKEND_DIR"
  mvn -q compile dependency:build-classpath \
    -Dmdep.outputFile=target/runtime-classpath.txt \
    -Dmdep.includeScope=runtime
  runtime_classpath="$(tr -d '\r\n' < target/runtime-classpath.txt)"
  "$JAVA_HOME/bin/java" -cp "target/classes;${runtime_classpath}" com.tablebite.Main
) &
backend_pid=$!

if [[ ! -d "$FRONTEND_DIR/node_modules/jsqr" || ! -d "$FRONTEND_DIR/node_modules/qrcode" ]]; then
  echo "Installing frontend dependencies..."
  (cd "$FRONTEND_DIR" && npm install)
fi

echo "Starting frontend..."
(
  cd "$FRONTEND_DIR"
  npm run dev
) &
frontend_pid=$!

echo ""
echo "TableBite is starting:"
echo "  Customer app : http://localhost:5173/"
echo "  Demo table   : http://localhost:5173/table/tablebite-demo-t01-8c9d4d1e"
echo "  Kitchen      : http://localhost:5173/kitchen  (kitchen code: $KITCHEN_ACCESS_CODE)"
echo "  Admin        : http://localhost:5173/staff/login  (admin@tablebite.local / Admin@12345 on first run)"
echo "  Phones on the same Wi-Fi: use the 'Network' URL Vite prints below."
echo "Press Ctrl+C to stop both services."

wait "$backend_pid" "$frontend_pid"
