#!/usr/bin/env bash
# deploy.sh — one-shot release of mock-brevo.
#
#   1. verify git state (clean-ish + remote configured)
#   2. compile + test Java
#   3. build multi-arch Docker image
#   4. push image to Docker Hub ($DOCKERHUB_USER/mock-brevo:<version>, :latest)
#   5. create and push git tag v<version> → triggers the GHA release workflow
#      which publishes to GHCR (ghcr.io/<owner>/mock-brevo:<version>)
#
# Required before running:
#   • docker login          (Docker Hub, with a PAT)
#   • git remote add origin <github-url>   (for the tag push)
#   • version already bumped with scripts/new_version.sh
#
# Usage:
#   scripts/deploy.sh [flags]
#
# Flags:
#   -n, --dry-run            build everything but don't push to docker / don't push git tag
#       --skip-tests         skip ./mvnw verify
#       --skip-docker        skip docker build + push (only tag + push git)
#       --skip-git-push      create the git tag locally but don't push it
#   -y, --yes                assume "yes" on every confirmation prompt
#       --platforms LIST     comma-separated buildx platforms (default: linux/amd64,linux/arm64)
#       --dockerhub-user U   Docker Hub username (default: cboleis)
#       --source-url URL     override the OCI source label (auto-detected from origin)
#   -h, --help               show this help
#
# Every flag is also settable via its env var equivalent:
#   DRY_RUN, SKIP_TESTS, SKIP_DOCKER, SKIP_GIT_PUSH, ASSUME_YES,
#   PLATFORMS, DOCKERHUB_USER, SOURCE_URL.

set -euo pipefail

cd "$(dirname "$0")/.."

# --- defaults (env vars → defaults → flags override below) ---------------
DOCKERHUB_USER="${DOCKERHUB_USER:-cboleis}"
PLATFORMS="${PLATFORMS:-linux/amd64,linux/arm64}"
SKIP_TESTS="${SKIP_TESTS:-false}"
SKIP_DOCKER="${SKIP_DOCKER:-false}"
SKIP_GIT_PUSH="${SKIP_GIT_PUSH:-false}"
DRY_RUN="${DRY_RUN:-false}"
ASSUME_YES="${ASSUME_YES:-false}"
SOURCE_URL="${SOURCE_URL:-}"

usage() {
  # Extract the leading block comment (everything between the shebang and the
  # first non-comment, non-blank line) and strip the "# " prefix.
  awk 'NR==1{next} /^[^#]/ && NF {exit} {sub(/^# ?/,""); print}' "$0"
}

# --- arg parsing ---------------------------------------------------------
while [ $# -gt 0 ]; do
  case "$1" in
    -n|--dry-run)         DRY_RUN=true ;;
    --skip-tests)         SKIP_TESTS=true ;;
    --skip-docker)        SKIP_DOCKER=true ;;
    --skip-git-push)      SKIP_GIT_PUSH=true ;;
    -y|--yes)             ASSUME_YES=true ;;
    --platforms)          PLATFORMS="${2:?--platforms requires a value}"; shift ;;
    --platforms=*)        PLATFORMS="${1#*=}" ;;
    --dockerhub-user)     DOCKERHUB_USER="${2:?--dockerhub-user requires a value}"; shift ;;
    --dockerhub-user=*)   DOCKERHUB_USER="${1#*=}" ;;
    --source-url)         SOURCE_URL="${2:?--source-url requires a value}"; shift ;;
    --source-url=*)       SOURCE_URL="${1#*=}" ;;
    -h|--help)            usage; exit 0 ;;
    --)                   shift; break ;;
    -*)                   echo "Unknown flag: $1" >&2; echo; usage; exit 2 ;;
    *)                    echo "Unexpected argument: $1" >&2; echo; usage; exit 2 ;;
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
  if [ "${ASSUME_YES:-false}" = "true" ]; then return 0; fi
  read -rp "$prompt [y/N] " yn
  [[ "$yn" =~ ^[Yy]$ ]]
}

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

# --- preflight ------------------------------------------------------------
command -v docker >/dev/null 2>&1 || { err "docker not found on PATH"; exit 1; }
[ -f pom.xml ] || { err "Run from repo root (no pom.xml here)"; exit 1; }
[ -x scripts/deploy.sh ] || warn "scripts/deploy.sh is not executable — you're running it anyway, OK"

VERSION=$(current_version)
[ -n "$VERSION" ] || { err "Cannot read project version from pom.xml"; exit 1; }

TAG="v$VERSION"
IMAGE="$DOCKERHUB_USER/mock-brevo"

step "Release mock-brevo $VERSION"
echo "  Docker image : $IMAGE:$VERSION  +  $IMAGE:latest"
echo "  Git tag      : $TAG"
echo "  Platforms    : $PLATFORMS"
echo "  Dry run      : $DRY_RUN"
echo

# --- check working tree ---------------------------------------------------
if [ -n "$(git status --porcelain 2>/dev/null || true)" ]; then
  warn "Git working tree is dirty:"
  git status --short
  confirm "Continue without committing these changes?" || exit 1
fi

# --- check docker login ---------------------------------------------------
if [ "$SKIP_DOCKER" != "true" ] && [ "$DRY_RUN" != "true" ]; then
  step "Checking Docker Hub auth"
  # `docker info` isn't reliable; test with a real auth header instead
  if ! docker system info 2>/dev/null | grep -qi "username:"; then
    warn "Cannot verify Docker Hub login via 'docker system info'"
    warn "If the push fails later: docker login -u $DOCKERHUB_USER"
  else
    ok "Docker Hub: logged in"
  fi
fi

# --- check git tag doesn't already exist ---------------------------------
if git rev-parse "$TAG" >/dev/null 2>&1; then
  warn "Git tag $TAG already exists locally"
  confirm "Delete and recreate it?" || {
    err "Aborted — bump the version with scripts/new_version.sh first"
    exit 1
  }
  git tag -d "$TAG"
fi

# --- check git tag doesn't already exist on remote -----------------------
if [ "$SKIP_GIT_PUSH" != "true" ] && git remote get-url origin >/dev/null 2>&1; then
  if git ls-remote --exit-code --tags origin "refs/tags/$TAG" >/dev/null 2>&1; then
    err "Tag $TAG already exists on origin — releases cannot be overwritten"
    err "Pick a new version with scripts/new_version.sh"
    exit 1
  fi
fi

confirm "Proceed with release?" || { warn "Aborted"; exit 0; }

# --- 1 & 2. compile + test -----------------------------------------------
if [ "$SKIP_TESTS" = "true" ]; then
  step "Building (tests skipped)"
  ./mvnw -B -ntp -DskipTests package
else
  step "Compiling + running tests (./mvnw verify)"
  ./mvnw -B -ntp verify
fi
ok "Build OK"

# --- 3 & 4. docker image --------------------------------------------------
if [ "$SKIP_DOCKER" = "true" ]; then
  warn "SKIP_DOCKER=true — skipping image build/push"
else
  step "Docker buildx: $IMAGE:$VERSION + :latest ($PLATFORMS)"

  # Ensure builder exists (idempotent)
  if ! docker buildx inspect mock-brevo-builder >/dev/null 2>&1; then
    docker buildx create --name mock-brevo-builder --use
  else
    docker buildx use mock-brevo-builder
  fi

  # Source URL best-effort: derive from origin remote, fallback to hub
  SOURCE_URL="${SOURCE_URL:-}"
  if [ -z "$SOURCE_URL" ] && git remote get-url origin >/dev/null 2>&1; then
    SOURCE_URL=$(git remote get-url origin | sed -E 's#\.git$##; s#^git@github\.com:#https://github.com/#')
  fi
  SOURCE_URL="${SOURCE_URL:-https://hub.docker.com/r/$IMAGE}"

  PUSH_FLAG="--push"
  if [ "$DRY_RUN" = "true" ]; then
    warn "DRY_RUN — building but not pushing"
    PUSH_FLAG=""
  fi

  # shellcheck disable=SC2086
  docker buildx build \
    --platform "$PLATFORMS" \
    --tag "$IMAGE:$VERSION" \
    --tag "$IMAGE:latest" \
    --build-arg IMAGE_VERSION="$VERSION" \
    --build-arg SOURCE_URL="$SOURCE_URL" \
    $PUSH_FLAG \
    .
  ok "Docker image ready"
fi

# --- 5. git tag + push ----------------------------------------------------
step "Creating git tag $TAG"
git tag -a "$TAG" -m "Release $VERSION"
ok "Tag $TAG created locally"

if [ "$SKIP_GIT_PUSH" = "true" ] || [ "$DRY_RUN" = "true" ]; then
  warn "Not pushing the tag (SKIP_GIT_PUSH=$SKIP_GIT_PUSH, DRY_RUN=$DRY_RUN)"
  echo "  → push manually when ready: git push origin $TAG"
elif ! git remote get-url origin >/dev/null 2>&1; then
  warn "No 'origin' remote configured — tag stays local"
  echo "  → git remote add origin git@github.com:<you>/mock-brevo.git"
  echo "  → git push origin $TAG"
else
  step "Pushing tag to origin (triggers GHA release workflow → GHCR)"
  git push origin "$TAG"
  REMOTE=$(git remote get-url origin | sed -E 's#\.git$##; s#^git@github\.com:#https://github.com/#')
  ok "Tag pushed — watch: $REMOTE/actions"
fi

# --- summary --------------------------------------------------------------
echo
ok "Release $VERSION complete"
echo
if [ "$DRY_RUN" != "true" ] && [ "$SKIP_DOCKER" != "true" ]; then
  echo "Docker Hub:"
  echo "  docker pull $IMAGE:$VERSION"
  echo "  docker pull $IMAGE:latest"
  echo "  https://hub.docker.com/r/$IMAGE"
  echo
fi
if git remote get-url origin >/dev/null 2>&1 && [ "$SKIP_GIT_PUSH" != "true" ] && [ "$DRY_RUN" != "true" ]; then
  echo "GitHub release workflow running at:"
  echo "  $(git remote get-url origin | sed -E 's#\.git$##; s#^git@github\.com:#https://github.com/#')/actions"
fi
