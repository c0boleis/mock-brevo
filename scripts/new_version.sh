#!/usr/bin/env bash
# new_version.sh — bumps mock-brevo's version, updates CHANGELOG.md, commits,
# and creates + pushes the release tag v<version>.
#
# Pushing the tag triggers the GitHub Actions release workflow, which builds
# the multi-arch Docker image and publishes it to GHCR (ghcr.io/<owner>/mock-brevo)
# and Docker Hub (cboleis/mock-brevo), plus syncs the README to Docker Hub.
#
# Usage:
#   scripts/new_version.sh                         # prompts for the new version
#   scripts/new_version.sh 0.2.0                   # non-interactive version
#   scripts/new_version.sh 0.2.0 -m "Fixed X, added Y"
#   scripts/new_version.sh 0.2.0 -F release-notes.md
#   scripts/new_version.sh 0.2.0 -e                # opens $EDITOR for the message
#
# Flags:
#   -m, --message TEXT      changelog body for this version (disables the
#                           default "move [Unreleased] content" behavior)
#   -F, --message-file FILE read the changelog body from FILE
#   -e, --edit              open $EDITOR (or vi) to write the changelog body
#   -y, --yes               assume "yes" on every confirmation prompt
#       --bump-only         stop after updating pom.xml + CHANGELOG.md (no commit, no tag)
#       --no-push           create the tag locally but don't push it
#   -h, --help              show this help
#
# Every flag is also settable via its env var equivalent:
#   MESSAGE, MESSAGE_FILE, EDIT_MESSAGE, ASSUME_YES, BUMP_ONLY, NO_PUSH.
#
# Changelog rules:
#   - With a message (-m / -F / -e): the new version section is inserted
#     AFTER [Unreleased], with the provided body. [Unreleased] stays as-is.
#   - Without a message: [Unreleased] content is moved under the new version
#     (the classic "Keep a Changelog" release workflow).

set -euo pipefail

cd "$(dirname "$0")/.."

# --- defaults -------------------------------------------------------------
MESSAGE="${MESSAGE:-}"
MESSAGE_FILE="${MESSAGE_FILE:-}"
EDIT_MESSAGE="${EDIT_MESSAGE:-false}"
ASSUME_YES="${ASSUME_YES:-false}"
BUMP_ONLY="${BUMP_ONLY:-false}"
NO_PUSH="${NO_PUSH:-false}"
NEW_VERSION=""

usage() {
  awk 'NR==1{next} /^[^#]/ && NF {exit} {sub(/^# ?/,""); print}' "$0"
}

# --- arg parsing ----------------------------------------------------------
while [ $# -gt 0 ]; do
  case "$1" in
    -m|--message)         MESSAGE="${2:?--message requires a value}"; shift ;;
    --message=*)          MESSAGE="${1#*=}" ;;
    -F|--message-file)    MESSAGE_FILE="${2:?--message-file requires a value}"; shift ;;
    --message-file=*)     MESSAGE_FILE="${1#*=}" ;;
    -e|--edit)            EDIT_MESSAGE=true ;;
    -y|--yes)             ASSUME_YES=true ;;
    --bump-only)          BUMP_ONLY=true ;;
    --no-push)            NO_PUSH=true ;;
    -h|--help)            usage; exit 0 ;;
    --)                   shift; break ;;
    -*)                   echo "Unknown flag: $1" >&2; echo; usage; exit 2 ;;
    *)
      if [ -z "$NEW_VERSION" ]; then NEW_VERSION="$1"
      else echo "Unexpected argument: $1" >&2; echo; usage; exit 2
      fi
      ;;
  esac
  shift
done

# --- helpers --------------------------------------------------------------
c_step=$'\033[1;36m'; c_ok=$'\033[32m'; c_warn=$'\033[33m'; c_err=$'\033[31m'; c_nc=$'\033[0m'
step() { printf "\n%s▸ %s%s\n" "$c_step" "$*" "$c_nc"; }
ok()   { printf "%s✓ %s%s\n"  "$c_ok"   "$*" "$c_nc"; }
warn() { printf "%s! %s%s\n"  "$c_warn" "$*" "$c_nc"; }
err()  { printf "%s✗ %s%s\n"  "$c_err"  "$*" "$c_nc" >&2; }

confirm() {
  local prompt="${1:-Continue?}"
  if [ "$ASSUME_YES" = "true" ]; then return 0; fi
  read -rp "$prompt [y/N] " yn
  [[ "$yn" =~ ^[Yy]$ ]]
}

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

# --- resolve version ------------------------------------------------------
[ -f pom.xml ] || { err "Run from repo root (no pom.xml here)"; exit 1; }

CURRENT=$(current_version)
[ -n "$CURRENT" ] || { err "Cannot read current version from pom.xml"; exit 1; }

if [ -z "$NEW_VERSION" ]; then
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

TAG="v$NEW_VERSION"

# --- resolve message (from -m / -F / -e) ---------------------------------
# Enforce mutual exclusivity among message sources
msg_sources=0
[ -n "$MESSAGE" ]       && msg_sources=$((msg_sources + 1))
[ -n "$MESSAGE_FILE" ]  && msg_sources=$((msg_sources + 1))
[ "$EDIT_MESSAGE" = "true" ] && msg_sources=$((msg_sources + 1))
if [ "$msg_sources" -gt 1 ]; then
  err "--message, --message-file and --edit are mutually exclusive"
  exit 1
fi

if [ -n "$MESSAGE_FILE" ]; then
  [ -f "$MESSAGE_FILE" ] || { err "Message file not found: $MESSAGE_FILE"; exit 1; }
  MESSAGE=$(cat "$MESSAGE_FILE")
fi

if [ "$EDIT_MESSAGE" = "true" ]; then
  editor_file=$(mktemp --suffix=.md)
  {
    echo "# Enter the changelog body for version $NEW_VERSION."
    echo "# Lines starting with '#' are removed."
    echo "# Leave empty to abort."
    echo
  } > "$editor_file"
  "${EDITOR:-vi}" "$editor_file"
  MESSAGE=$(grep -v '^#' "$editor_file" | sed -e '/./,$!d' | awk 'NF{p=NR} {buf[NR]=$0} END{for(i=1;i<=p;i++) print buf[i]}')
  rm -f "$editor_file"
  [ -n "$MESSAGE" ] || { err "Empty changelog message — aborted"; exit 1; }
fi

# --- preflight (skipped in --bump-only) -----------------------------------
if [ "$BUMP_ONLY" != "true" ]; then
  if [ -n "$(git status --porcelain 2>/dev/null || true)" ]; then
    err "Git working tree is dirty — commit or stash changes before releasing:"
    git status --short
    exit 1
  fi

  if git rev-parse "$TAG" >/dev/null 2>&1; then
    err "Git tag $TAG already exists locally — pick another version"
    exit 1
  fi

  if [ "$NO_PUSH" != "true" ] && git remote get-url origin >/dev/null 2>&1; then
    if git ls-remote --exit-code --tags origin "refs/tags/$TAG" >/dev/null 2>&1; then
      err "Tag $TAG already exists on origin — releases cannot be overwritten"
      exit 1
    fi
  fi
fi

# --- 1. bump pom.xml ------------------------------------------------------
step "1. pom.xml: $CURRENT → $NEW_VERSION"
./mvnw -B -q -ntp versions:set -DnewVersion="$NEW_VERSION" -DgenerateBackupPoms=false
ok "pom.xml updated"

# --- 2. update CHANGELOG.md -----------------------------------------------
step "2. CHANGELOG.md: inserting new section"
TODAY=$(date -u +%Y-%m-%d)
CHANGELOG_UPDATED=false

if [ ! -f CHANGELOG.md ]; then
  warn "CHANGELOG.md not found — skipping"
elif grep -q "^## \[$NEW_VERSION\]" CHANGELOG.md 2>/dev/null; then
  warn "CHANGELOG already has a section for $NEW_VERSION — skipping"
elif [ -n "$MESSAGE" ]; then
  # Insert new version section AFTER [Unreleased], with the provided body.
  # [Unreleased] is left unchanged.
  msg_file=$(mktemp)
  printf '%s\n' "$MESSAGE" > "$msg_file"
  tmp=$(mktemp)
  awk -v v="$NEW_VERSION" -v d="$TODAY" -v mf="$msg_file" '
    function emit() {
      print "## [" v "] — " d
      print ""
      while ((getline line < mf) > 0) print line
      close(mf)
      print ""
    }
    /^## \[Unreleased\]$/ { in_unrel=1; print; next }
    in_unrel && /^## / {
      emit()
      in_unrel=0
      # fall through to print current line
    }
    { print }
    END { if (in_unrel) emit() }
  ' CHANGELOG.md > "$tmp"
  mv "$tmp" CHANGELOG.md
  rm -f "$msg_file"
  CHANGELOG_UPDATED=true
  ok "CHANGELOG.md updated ([$NEW_VERSION] section inserted with provided body)"
else
  # Default: move [Unreleased] content under the new version header.
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
  CHANGELOG_UPDATED=true
  ok "CHANGELOG.md updated (moved [Unreleased] content under [$NEW_VERSION])"
fi

if [ "$BUMP_ONLY" = "true" ]; then
  echo
  ok "Version bumped to $NEW_VERSION (--bump-only; no commit, no tag)"
  exit 0
fi

# --- 3. review changelog --------------------------------------------------
step "3. Review CHANGELOG.md"
if [ -n "$MESSAGE" ]; then
  echo "  Body provided on the command line — check it rendered as expected."
else
  echo "  Fill in what this version contains (edit in another window if needed)."
fi
confirm "Continue with commit + tag + push?" || {
  warn "Aborted — pom.xml and CHANGELOG.md were modified but not committed"
  warn "  → revert: git checkout -- pom.xml CHANGELOG.md"
  exit 1
}

# --- 4. commit ------------------------------------------------------------
step "4. Committing release"
git add pom.xml
[ "$CHANGELOG_UPDATED" = "true" ] && git add CHANGELOG.md
git commit -m "Release $NEW_VERSION"
ok "Commit created"

# --- 5. tag ---------------------------------------------------------------
step "5. Creating annotated tag $TAG"
git tag -a "$TAG" -m "Release $NEW_VERSION"
ok "Tag $TAG created locally"

# --- 6. push --------------------------------------------------------------
if [ "$NO_PUSH" = "true" ]; then
  warn "--no-push set — nothing pushed"
  echo "  → push when ready: git push origin HEAD && git push origin $TAG"
elif ! git remote get-url origin >/dev/null 2>&1; then
  warn "No 'origin' remote configured — nothing pushed"
  echo "  → git remote add origin git@github.com:<you>/mock-brevo.git"
  echo "  → git push origin HEAD && git push origin $TAG"
else
  CURRENT_BRANCH=$(git branch --show-current)
  if [ -z "$CURRENT_BRANCH" ]; then
    err "Detached HEAD — refusing to push. Check out a branch first, then:"
    echo "  → git push origin <branch> && git push origin $TAG"
    exit 1
  fi
  step "6. Pushing branch + tag to origin (triggers GHA release workflow)"
  git push origin "$CURRENT_BRANCH"
  git push origin "$TAG"
  REMOTE=$(git remote get-url origin | sed -E 's#\.git$##; s#^git@github\.com:#https://github.com/#')
  ok "Pushed — watch: $REMOTE/actions"
fi

# --- summary --------------------------------------------------------------
echo
ok "Release $NEW_VERSION queued"
echo
if [ "$NO_PUSH" != "true" ] && git remote get-url origin >/dev/null 2>&1; then
  OWNER=$(git remote get-url origin | sed -E 's#\.git$##; s#^git@github\.com:##; s#^https://github\.com/##' | cut -d/ -f1)
  echo "The release workflow will publish:"
  echo "  ghcr.io/$OWNER/mock-brevo:$NEW_VERSION (+ :latest)"
  echo "  cboleis/mock-brevo:$NEW_VERSION         (+ :latest)"
  echo "and sync the README to https://hub.docker.com/r/cboleis/mock-brevo"
fi
