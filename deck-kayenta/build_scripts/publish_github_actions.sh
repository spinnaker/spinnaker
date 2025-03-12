#!/bin/bash

# These scripts are based on similar scripts in https://github.com/spinnaker/deck

# Check that this is only run by GitHub Actions
echo "Kayenta package publisher ---> Checking that this script is run by GitHub Actions."
if [ "x${GITHUB_ACTIONS}" != "xtrue" ] ; then
  echo "This publish script should only be run by GitHub Actions and is not meant to be run locally."
  exit 2
fi

# Check that the last commit modifying package.json contains a version bump ONLY
CWD=$(pwd)
LAST_PKGJSON_COMMIT=$(git log -n 1 --pretty=format:%H -- "package.json")
echo "Kayenta package publisher ---> Checking that the last commit (${LAST_PKGJSON_COMMIT}) contains a version bump ONLY..."
HAS_PKG_BUMP=$(git diff "${LAST_PKGJSON_COMMIT}..${LAST_PKGJSON_COMMIT}~1" -- "package.json" | grep -c '"version"')
if [ "$HAS_PKG_BUMP" -ne 0 ] ; then
  echo "Kayenta package publisher ---> Version bump detected. Checking that it is a pure package bump..."
  ./build_scripts/assert_package_bump.sh HEAD^ || exit 11
else
  echo "Kayenta package publisher ---> The last commit (${LAST_PKGJSON_COMMIT}) did not contain a version bump..."
  echo "=========================================="
  echo ""
  git diff "${LAST_PKGJSON_COMMIT}..${LAST_PKGJSON_COMMIT}~1" -- "package.json"
  echo ""
  echo "=========================================="
  echo "Kayenta package publisher ---> The last commit (${LAST_PKGJSON_COMMIT}) did not contain a version bump. Exiting without publishing."
  exit 42
fi
echo "Kayenta package publisher ---> Looks good! Version bump was the only change. Let's get publishing..."

# Run yarn
echo "Kayenta package publisher ---> Updating to latest dependencies..."
yarn

# Determine upstream dependencies and proper build order
echo "Kayenta package publisher ---> Preparing to publish..."
npm publish

exit 0
