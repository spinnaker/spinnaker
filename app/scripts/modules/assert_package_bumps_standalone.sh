#!/bin/bash
# Reports if package bumps are combined with other changes (not allowed). Package bumps must be standalone.
# cd `dirname $0`;

PKGJSONCHANGED="Version change detected in package.json"
ONLYVERSIONCHANGED="Version change must be the only line changed in package.json"
ONLYPKGJSONCHANGED="package.json (in app/scripts/modules) must be the only files changed in a pull request with version bumps"

TARGET_BRANCH=origin/master
echo "TARGET_BRANCH=$TARGET_BRANCH"

# Tests are run against an ephemeral merge commit so we don't have to merge in $TARGET_BRANCH

for PKGJSON in `ls app/scripts/modules/*/package.json` ; do
  MODULE=`basename $(dirname $PKGJSON)`
  echo "::group::Checking $MODULE"
  echo "==================================================="
  echo "Checking $MODULE"
  echo "==================================================="
  HAS_PKG_BUMP=`git diff $TARGET_BRANCH -- $PKGJSON | grep '"version"' | wc -l`
  if [ $HAS_PKG_BUMP -ne 0 ] ; then
    echo " [ YES  ] $PKGJSONCHANGED"

    # Ensuring that the version change is the only change in package.json
    PKG_JSON_OTHER_CHANGES=`git diff --numstat $TARGET_BRANCH -- $PKGJSON | cut -f 1`
    if [ $PKG_JSON_OTHER_CHANGES -ne 1 ] ; then
      echo " [ FAIL ] $ONLYVERSIONCHANGED"
      echo ""
      echo "=========================================="
      echo "Failure details (git diff of package.json)"
      echo "=========================================="
      echo ""
      git diff $TARGET_BRANCH -- $PKGJSON
      echo ""
      echo "=========================================="
      exit 2
    else
      echo " [ PASS ] $ONLYVERSIONCHANGED"
      echo "::endgroup::"
    fi

    # checking that the only files changed are app/scripts/modules/*/package.json
    OTHER_FILES_CHANGED=`git diff --name-only $TARGET_BRANCH | grep -v "app/scripts/modules/.*/package.json" | wc -l`
    if [ $OTHER_FILES_CHANGED -ne 0 ] ; then
      echo " [ FAIL ] $ONLYPKGJSONCHANGED"
      echo ""
      echo "==========================================="
      echo "Failure details (list of all files changed)"
      echo "==========================================="
      echo ""
      git diff --name-only $TARGET_BRANCH
      echo ""
      echo "==========================================="
      exit 1
    else
      echo " [ PASS ] $ONLYPKGJSONCHANGED"
    fi
  else
    echo " [  NO  ] $PKGJSONCHANGED"
    echo " [  N/A ] $ONLYVERSIONCHANGED"
    echo " [  N/A ] $ONLYPKGJSONCHANGED"
  fi
  echo ""
done

