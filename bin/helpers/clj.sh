
function set_common_vars() {
  CLJ_DIR=${CLJ_DIR:-${PROJECT_DIR}}
  CLJ_MAIN=${CLJ_MAIN:-"leihs.${PROJECT_NAME}.main"}
  ARTIFACT_NAME=${ARTIFACT_NAME:-"leihs-${PROJECT_NAME}-js"}
  LEIHS_UI_DIR=${LEIHS_UI_DIR:-"${PROJECT_DIR}/leihs-ui"}
  JS_ASSETS_DIR=${JS_ASSETS_DIR:-"resources/public/${PROJECT_NAME}/js"}
  JS_BUILD_DIR=${JS_BUILD_DIR:-"resources/public/${PROJECT_NAME}/js/cljs-runtime"}
}

function debug_setup() {
  DEBUG=${DEBUG:-NO}
  if [ "$DEBUG" == "YES" ]; then
    set -x
  fi
}


### CLJ #######################################################################

function clj_setup_env() {
  cd $PROJECT_DIR
  $PROJECT_DIR/bin/env/java-setup
  $PROJECT_DIR/bin/env/clojure-setup
  cd -
}

function clj_uberjar() {
  debug_setup
  set_common_vars
  clj_setup_env
  cd $CLJ_DIR
  clojure -T:build uber
  cd -
}

function clj_run() {
  debug_setup
  set_common_vars
  clj_setup_env
  cd $CLJ_DIR
  clj -M -m ${CLJ_MAIN} "$@"
  cd -
}

function clj_outdated() {
  debug_setup
  set_common_vars
  clj_setup_env
  cd $CLJ_DIR
  clojure -M:outdated "$@"
  cd -
}


### leihs-UI ##################################################################


function ui_prepare() {
  cd ${LEIHS_UI_DIR}
  test -d node_modules && npm i || { npm ci || npm i ;} || exit 1
  npm run build:theme || exit 1
  npm run build:ssr || exit 1
  cd -
}

function ui_setup() {
  set_common_vars
  ${LEIHS_UI_DIR}/bin/build || ui_prepare
}


### CLJS ######################################################################

function cljs_setup_env() {
  cd $PROJECT_DIR
  $PROJECT_DIR/bin/env/java-setup
  $PROJECT_DIR/bin/env/clojure-setup
  $PROJECT_DIR/bin/env/nodejs-setup
  cd -
}

function cljs-build() {
  debug_setup
  set_common_vars
  cljs_setup_env
  cd $PROJECT_DIR
  npx shadow-cljs compile $ARTIFACT_NAME
  cd -
}

function cljs-clean() {
  debug_setup
  set_common_vars
  cd $PROJECT_DIR
  rm -rf ${JS_ASSETS_DIR}
  cd -
}

function cljs-release() {
  debug_setup
  set_common_vars
  cljs_setup_env
  cd $PROJECT_DIR
  npm ci --no-audit
  # `--debug` option does tree shaking but keeps the original name
  npx shadow-cljs release $ARTIFACT_NAME "$@"
  rm -rf ${JS_BUILD_DIR}
  cd -
}

function cljs-watch() {
  debug_setup
  set_common_vars
  cljs_setup_env
  cd $PROJECT_DIR
  npm i
  npx shadow-cljs watch $ARTIFACT_NAME "$@"
  cd -
}
