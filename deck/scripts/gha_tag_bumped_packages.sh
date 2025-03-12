#!/bin/bash

# Run this script from the 'deck/packages' directory
cd "$(dirname "$0")/../packages" || exit 255

if [ "x${GITHUB_ACTIONS}" != "xtrue" ] ; then
  echo "This publish script should only be run by GitHub Actions and is not meant to be run locally."
  exit 2
fi

TAGGED=false

# Determine what packages were bumped in the pushed commit and tag them
for PACKAGEJSON in */package.json ; do
  HAS_PKG_BUMP=$(git diff HEAD^ -- "${PACKAGEJSON}" | grep -c '"version"')
  if [ "$HAS_PKG_BUMP" -ne 0 ] ; then
    echo "Deck package publisher ---> Version bump detected in $PACKAGEJSON"
    TAG=$(jq -r '.name + "@" + .version' < $PACKAGEJSON)
    git tag -a $TAG -m "Publish $TAG to NPM"
    git push origin $TAG
    TAGGED=true
  fi
done

## Github Action Step output variable: tagged
echo ::set-output name=tagged::$TAGGED
