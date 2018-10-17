#!/bin/bash -e

# Params to be passed in
MODULES_TO_BE_BUILT=("$@")  # optional, if no list of modules are provided, we'll go do all of them except SKIPPED_MODULES

cd "$(dirname "$0")/.."
PROJECT_ROOT=$(pwd)

source ~/.nvm/nvm.sh
NODE_JS_VERSION=`node -e 'console.log(require("./package.json").engines.node.replace(/[^\d\.]/g, ""))'`;
nvm install ${NODE_JS_VERSION}

# go find all the modules and add them
if [[ ${#MODULES_TO_BE_BUILT[0]} -eq 0 ]]; then
  SKIPPED_MODULES=("dcos" "azure" "canary" "oracle")  # skipped modules that are not following the module format

  MODULES_TO_BE_BUILT=("core") # enforce module build order

  for MODULE_PATH in app/scripts/modules/* ; do
    MODULE=$(basename ${MODULE_PATH})

    if [[ " ${SKIPPED_MODULES[@]} " =~ " ${MODULE} " ]]; then
      echo "Skipping module '${MODULE}' because it's not currently setup for module builds"
      continue
    fi

    if [[ ! -d ${MODULE_PATH} ]]; then
      continue
    fi

    # only add the to the list if if it's not already in there
    if [[ ! " ${MODULES_TO_BE_BUILT[@]} " =~ " ${MODULE} " ]]; then
      MODULES_TO_BE_BUILT+=("${MODULE}")
    fi
  done
fi

echo
echo "Modules found: ${MODULES_TO_BE_BUILT[@]}"

# make sure each module is buildable (which lints)
for MODULE in ${MODULES_TO_BE_BUILT[@]}; do
  echo "Building module '${MODULE}'..."
  cd ${PROJECT_ROOT}/app/scripts/modules/${MODULE}
  yarn lib
done
