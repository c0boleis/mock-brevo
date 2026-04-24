#!/usr/bin/env bash
# new_version.sh — bumps the version in pom.xml and inserts a new CHANGELOG
# section. Does NOT commit, does NOT push — run deploy.sh afterwards.
#
# Usage:
#   scripts/new_version.sh                 # prompts for the new version
#   scripts/new_version.sh 0.2.0           # non-interactive

set -euo pipefail

cd "$(dirname "$0")/.."

# --- helpers --------------------------------------------------------------
c_step=$'\033[1;36m'; c_ok=$'\033[32m'; c_warn=$'\033[33m'; c_err=$'\033[31m'; c_nc=$'\033[0m'
step() { printf "%s→ %s%s\n" "$c_step" "$*" "$c_nc"; }
ok()   { printf "%s✓ %s%s\n" "$c_ok"   "$*" "$c_nc"; }
warn() { printf "%s! %s%s\n" "$c_warn" "$*" "$c_nc"; }
err()  { printf "%s✗ %s%s\n" "$c_err"  "$*" "$c_nc" >&2; }

# Read the <version> of the <project> (not the parent). Robust against the
# fact that the Spring Boot parent has its own <version> earlier in the file.
current_version() {
  awk '
    /<artifactId>mock-brevo<\/artifactId>/ { found=1; next }
    found && /<version>/ {
      sub(/.*<version>/,"")
      sub(/<\/version>.*/,"")
      print
      exit
    }
  ' pom.xml
}

# --- inputs ---------------------------------------------------------------
CURRENT=$(current_version)
[ -n "$CURRENT" ] || { err "Cannot read current version from pom.xml"; exit 1; }

if [ $# -ge 1 ]; then
  NEW_VERSION="$1"
else
  printf "Current version: %s\n" "$CURRENT"
  read -rp "New version (e.g. 0.2.0): " NEW_VERSION
fi

# Semver-ish check (accepts 0.1.2 and 0.1.2-beta1, 1.0.0-rc.1, etc.)
if ! [[ "$NEW_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+([-.+][A-Za-z0-9.-]+)?$ ]]; then
  err "Invalid version: $NEW_VERSION"
  exit 1
fi

if [ "$NEW_VERSION" = "$CURRENT" ]; then
  err "New version is identical to current ($CURRENT)"
  exit 1
fi

# --- update pom.xml -------------------------------------------------------
step "1. pom.xml: $CURRENT → $NEW_VERSION"
./mvnw -B -q -ntp versions:set -DnewVersion="$NEW_VERSION" -DgenerateBackupPoms=false
ok "pom.xml updated"

# --- update CHANGELOG.md --------------------------------------------------
step "2. CHANGELOG.md: inserting new section"
TODAY=$(date -u +%Y-%m-%d)

if grep -q "^## \[$NEW_VERSION\]" CHANGELOG.md 2>/dev/null; then
  warn "CHANGELOG already has a section for $NEW_VERSION — skipping"
elif [ ! -f CHANGELOG.md ]; then
  warn "CHANGELOG.md not found — skipping"
else
  tmp=$(mktemp)
  awk -v v="$NEW_VERSION" -v d="$TODAY" '
    /^## \[Unreleased\]$/ {
      print
      print ""
      print "## [" v "] — " d
      next
    }
    { print }
  ' CHANGELOG.md > "$tmp"
  mv "$tmp" CHANGELOG.md
  ok "CHANGELOG.md updated (moved [Unreleased] content under [$NEW_VERSION])"
fi

# --- done -----------------------------------------------------------------
echo
ok "Version bumped to $NEW_VERSION"
echo
echo "Next steps:"
echo "  1. Review & edit CHANGELOG.md (fill in what this version contains)"
echo "  2. git diff"
echo "  3. git add pom.xml CHANGELOG.md && git commit -m \"Release $NEW_VERSION\""
echo "  4. scripts/deploy.sh   # runs tests, builds + pushes docker, tags git"
