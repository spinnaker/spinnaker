#!/bin/bash

PACKAGES_TO_PUBLISH="";

# Run this script from the 'deck/packages' directory
cd "$(dirname "$0")/../packages" || exit 255

if [ "x${GITHUB_ACTIONS}" != "xtrue" ] ; then
  echo "This publish script should only be run by GitHub Actions and is not meant to be run locally."
  exit 2
fi

BUMPS=no

# Determine what packages were bumped in the pushed commit and tag them
for PACKAGEJSON in */package.json ; do
  HAS_PKG_BUMP=$(git diff HEAD^ -- "${PACKAGEJSON}" | grep -c '"version"')
  if [ "$HAS_PKG_BUMP" -ne 0 ] ; then
    echo "Deck package publisher ---> Version bump detected in $PACKAGEJSON"
    TAG=$(jq -r '.name + "@" + .version' < package.json)
    git tag -a $TAG -m "Publish $TAG to NPM"
    git push origin $TAG
    BUMPS=yes
  fi
done

if [ "${BUMPS}" == "no" ] ; then
  echo "Nothing to publish."
  exit 0
fi

echo "Deck package publisher ---> yarn"
yarn

npx lerna publish from-git
