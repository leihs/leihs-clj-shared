SHARED_CLJ_DIR="${PROJECT_DIR}/shared-clj"
UI_DIR="${PROJECT_DIR}/leihs-ui"
ARTEFACT_PATH="$PROJECT_DIR/target/$PROJECT_NAME.jar"
DIGEST="$(git -C "$PROJECT_DIR" log -n 1 HEAD --pretty=%T)"
LOCAL_CACHE_DIR="${LOCAL_CACHE_DIR:-"${TMPDIR:-/tmp/}/leihs-build-cache"}"
LOCAL_CACHED_ARTEFACT_PATH="${LOCAL_CACHE_DIR}/${PROJECT_NAME}_${DIGEST}.jar"
BUILD_CACHE_DISABLED="${BUILD_CACHE_DISABLED:-NO}"

if [ $BUILD_CACHE_DISABLED == "YES" ] ||  [ $BUILD_CACHE_DISABLED == "NO" ]; then
  echo "BUILD_CACHE_DISABLED=$BUILD_CACHE_DISABLED"
  mkdir -p "$LOCAL_CACHE_DIR"
else
  echo 'BUILD_CACHE_DISABLED must be initially unset, or "YES", or "NO"'
  exit 1
fi


function pack {
  cp "$ARTEFACT_PATH" "$LOCAL_CACHED_ARTEFACT_PATH"
}

function extract {
  mkdir -p $(dirname $ARTEFACT_PATH)
  cp "$LOCAL_CACHED_ARTEFACT_PATH" "$ARTEFACT_PATH"
}

function build {
  if [ $BUILD_CACHE_DISABLED == "YES" ]; then
    echo "INFO: BUILD_CACHE_DISABLED is YES, building and nothing else"
    build_core
  else
    source ${SHARED_CLJ_DIR}/bin/require-clean-working-tree.sh
    require-clean-working-tree
    echo "LOCAL_CACHED_ARTEFACT_PATH: $LOCAL_CACHED_ARTEFACT_PATH"
    if [ -e $LOCAL_CACHED_ARTEFACT_PATH ]; then
      echo "INFO: locally cached artefact found, extracting, and caching ..."
      extract
    else
      build_core
      pack
    fi
  fi
}
