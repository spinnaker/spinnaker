#!/bin/bash

PACKAGES_TO_PUBLISH="";

# Run this script from the 'deck/packages' directory
cd "$(dirname "$0")/../packages" || exit 255

if [ "x${GITHUB_ACTIONS}" != "xtrue" ] ; then
  echo "This publish script should only be run by GitHub Actions and is not meant to be run locally."
  exit 2
fi

# Determine what packages were bumped in the pushed commit
for PACKAGEJSON in */package.json ; do
  HAS_PKG_BUMP=$(git diff HEAD^ -- "${PACKAGEJSON}" | grep -c '"version"')
  if [ "$HAS_PKG_BUMP" -ne 0 ] ; then
    echo "Deck package publisher ---> Version bump detected in $PACKAGEJSON"
    PACKAGES_TO_PUBLISH="$PACKAGES_TO_PUBLISH $(dirname $PACKAGEJSON) "
  fi
done

if [ "x${PACKAGES_TO_PUBLISH}" == "x" ] ; then
  echo "Nothing to publish."
  exit 0
fi

echo "Deck package publisher ---> yarn"
yarn

# Determine upstream dependencies and proper build order
BUILD_ORDER=$(../scripts/build_order.sh ${PACKAGES_TO_PUBLISH})
echo "Deck package publisher ---> Building packages:"
echo "${BUILD_ORDER}"
echo

# Loop over packages to build and either a) only build (if package is just a dependency) or b) build and publish

for PACKAGE in ${BUILD_ORDER} ; do
  pushd "$PACKAGE" > /dev/null || exit 5
  # Publish package if found in PACKAGES_TO_PUBLISH
  if [[ "${PACKAGES_TO_PUBLISH}" == *" ${PACKAGE} "* ]] ; then
    echo "Deck package publisher ---> Publishing ${PACKAGE}..."
    npm publish
    TAG=$(jq '.name + "@" + .version' < package.json)
    git tag $TAG
    git push origin $TAG
  else
    echo "Deck package publisher ---> Building (but not publishing) upstream dependency '${PACKAGE}'..."
    npm run prepublishOnly
  fi

  popd > /dev/null || exit 6
done
