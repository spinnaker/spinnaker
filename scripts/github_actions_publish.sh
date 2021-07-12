#!/bin/bash
MODULE=$1

# Run this script from the 'deck/scripts' directory
cd "$(dirname "$0")" || exit 255
pushd ../

# Show help text if no modules were specified
if [ "$1" == "" ] ; then
  echo "Publish a single module to npm. Version is expected to already be bumped."
  echo "$0 <module>"
  exit 1
fi

if [ $# -ne 1 ] ; then
  echo "This script is only meant to be run with a single module, but more than 1 was provided: $*"
  echo "$0 <module>"
fi

# Check that this is only run by GitHub Actions
echo "Deck package publisher ---> Checking that this script is run by GitHub Actions."
if [ "x${GITHUB_ACTIONS}" != "xtrue" ] ; then
  echo "This publish script should only be run by GitHub Actions and is not meant to be run locally."
  exit 2
fi

MODULES_DIR="packages"

# Check that the module exists and the last commit modifying <module>/package.json contains a version bump ONLY
echo "Deck package publisher ---> Checking that (${MODULE}) exist..."
if [ ! -e "${MODULES_DIR}/${MODULE}/package.json" ] ; then
  echo "${MODULES_DIR}/${MODULE}/package.json does not exist"
  exit 3
else
  LAST_PKGJSON_COMMIT=$(git log -n 1 --pretty=format:%H -- "${MODULES_DIR}/${MODULE}/package.json")
  echo "Deck package publisher ---> Checking that the last commit (${LAST_PKGJSON_COMMIT}) contains a version bump ONLY..."
  HAS_PKG_BUMP=$(git diff "${LAST_PKGJSON_COMMIT}..${LAST_PKGJSON_COMMIT}~1" -- "${MODULES_DIR}/${MODULE}/package.json" | grep -c '"version"')
  if [ "$HAS_PKG_BUMP" -ne 0 ] ; then
    echo "Deck package publisher ---> Version bump detected indeed. Checking that it is a pure package bump..."
    ./scripts/assert_package_bumps_standalone.sh HEAD^ || exit 11
  else
    echo "Deck package publisher ---> The last commit (${LAST_PKGJSON_COMMIT}) did not contain a version bump..."
    echo "=========================================="
    echo ""
    git diff "${LAST_PKGJSON_COMMIT}..${LAST_PKGJSON_COMMIT}~1" -- "${MODULES_DIR}/${MODULE}/package.json"
    echo ""
    echo "=========================================="
    echo "Deck package publisher ---> The last commit (${LAST_PKGJSON_COMMIT}) did not contain a version bump. Exiting without publishing."
    exit 42
  fi
  echo "Deck package publisher ---> Looks good! Version bump was the only change. Let's get publishing..."
fi

# Run yarn
echo "Deck package publisher ---> Updating to latest dependencies..."
yarn

# Determine upstream dependencies and proper build order
echo "Deck package publisher ---> Preparing to publish ${MODULE}..."
BUILDORDER=$(./scripts/build_order.sh "${MODULE}")
echo "Deck package publisher ---> Package build order:"
echo "${BUILDORDER}"
echo

# Loop over packages to build and either a) only build (if package is just a dependency) or b) build and publish

for DIR in ${BUILDORDER} ; do
  # Check if the current package to build is in PACKAGEDIRS (if so, publish it)
  pushd "${MODULES_DIR}/${DIR}" > /dev/null || exit 5
  if [ "${DIR}" == "${MODULE}" ] ; then
    echo "Deck package publisher ---> Publishing ${MODULE}..."
    yarn build
    npm publish
  else
    echo "Deck package publisher ---> Building (but not publishing) upstream dependency '${DIR}'..."
    yarn prepublishOnly
  fi

  popd > /dev/null || exit 6

done