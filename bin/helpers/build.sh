TMPDIR="${TMPDIR:-/tmp}" # fallback to /tmp if TMPDIR is not set, as in Ubuntu e.g.
BUILDCACHE_TMPDIR="${BUILDCACHE_TMPDIR:-$TMPDIR}"
BUILDCACHE_TMPDIR=${BUILDCACHE_TMPDIR%/} # remove trailing slash
mkdir -p $BUILDCACHE_TMPDIR
JAR_PATH="$PROJECT_DIR/target/$PROJECT_NAME.jar"

# JAR_PATH is usually a symlink into the cache after a previous build;
# remove it before building, otherwise the new jar is written through the
# link and overwrites (poisons) a cached jar of a different tree
function build_core_without_stale_jar {
  rm -f "$JAR_PATH"
  build_core
}

function build {
  if [[ -n $(git status -s) ]]; then
    echo "WARNING uncommitted changes in $PROJECT_NAME, (re)building from scratch, no linking"
    git status -v
    build_core_without_stale_jar
  else
    echo "OK no uncommitted changes, building or using cache"
    DIGEST="$(git -C "$PROJECT_DIR" log -n 1 HEAD --pretty=%T)"
    CACHED_JAR="${BUILDCACHE_TMPDIR}/${PROJECT_NAME}_${DIGEST}.jar"
    # a cached jar must be a regular file; a symlink is a leftover of the
    # write-through bug above and may point to the jar of another tree
    if [[ -L $CACHED_JAR ]]; then
      echo "WARNING discarding cached jar which is a symlink: $CACHED_JAR"
      rm -f "$CACHED_JAR"
    fi
    if [[ -e $CACHED_JAR ]]; then
      echo "using cached jar"
      mkdir -p "$(dirname "$JAR_PATH")"
      touch $CACHED_JAR
    else
      echo "no cached jar found, building"
      build_core_without_stale_jar
      mv -f $JAR_PATH $CACHED_JAR
    fi
    echo "linking $CACHED_JAR to $JAR_PATH"
    ln -sf $CACHED_JAR $JAR_PATH
  fi
}

# Clean cached jars older than a week
find $BUILDCACHE_TMPDIR -maxdepth 1 -name "${PROJECT_NAME}_*.jar" -type f -mtime +7 -delete
