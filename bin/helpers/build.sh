TMPDIR="${TMPDIR:-/tmp}" # fallback to /tmp if TMPDIR is not set, as in Ubuntu e.g.
BUILDCACHE_TMPDIR="${BUILDCACHE_TMPDIR:-$TMPDIR}"
BUILDCACHE_TMPDIR=${BUILDCACHE_TMPDIR%/} # remove trailing slash
mkdir -p $BUILDCACHE_TMPDIR
JAR_PATH="$PROJECT_DIR/target/$PROJECT_NAME.jar"

function build {
  if [[ -n $(git status -s) ]]; then
    echo "WARNING uncommitted changes in $PROJECT_NAME, (re)building from scratch, no linking"
    git status -v
    build_core
  else
    echo "OK no uncommitted changes, building or using cache"
    DIGEST="$(git -C "$PROJECT_DIR" log -n 1 HEAD --pretty=%T)"
    CACHED_JAR="${BUILDCACHE_TMPDIR}/${PROJECT_NAME}_${DIGEST}.jar"
    if [[ -e $CACHED_JAR ]]; then
      echo "using cached jar"
      mkdir -p $PROJECT_DIR/target
      touch $CACHED_JAR
    else
      echo "no cached jar found, building"
      build_core
      mv $JAR_PATH $CACHED_JAR
    fi
    echo "linking $CACHED_JAR to $JAR_PATH"
    ln -sf $CACHED_JAR $JAR_PATH
  fi
}

# Clean cached jars older than a week
find $BUILDCACHE_TMPDIR -maxdepth 1 -name "${PROJECT_NAME}_*.jar" -type f -mtime +7 -delete
