#!/bin/bash
# Usage: assert_package_bumps_standalone [target branch]
# Use [target branch] to change the branch the script checks for changes against
# (default: origin/master if running in a Github Action, master otherwise)

# Reports if package bumps are combined with other changes (not allowed). Package bumps must be standalone.
if [[ $GITHUB_EVENT_NAME == "pull_request" && ( $GITHUB_BASE_REF != "master" || $GITHUB_REPOSITORY != 'spinnaker/deck' ) ]] ; then
  echo "This is a pull request, but not to master -- exiting"
  exit 0
fi

cd "$(dirname "$0")" || exit 1;
if [[ -n $TRAVIS || -n $GITHUB_ACTIONS ]] ; then
  echo "git fetch -q..."
  git fetch -q
  CI_TARGET_BRANCH=origin/master # dirname should maybe work in both cases?
fi

# Use the command line argument, origin/master (if running on GHA) or master (in that order)
TARGET_BRANCH=${1}
TARGET_BRANCH=${TARGET_BRANCH:-${CI_TARGET_BRANCH}}
TARGET_BRANCH=${TARGET_BRANCH:-master}
echo "TARGET_BRANCH=$TARGET_BRANCH"
echo ""

# Run a git diff against TARGET_BRANCH outside of a pipe so it will exit with any failure code
git diff "$TARGET_BRANCH" -- . >/dev/null || exit $?

# Tests are run against an ephemeral merge commit so we don't have to merge in $TARGET_BRANCH

HAS_PURE_PKG_BUMP=false
for PKGJSON in ../packages/{amazon,appengine,azure,cloudfoundry,core,dcos,docker,ecs,google,huaweicloud,kubernetes,oracle,tencentcloud,titus}/package.json ; do
  MODULE=$(basename "$(dirname "$PKGJSON")")

  IS_PRIVATE_PKG=$(jq .private $PKGJSON)
  HAS_PKG_BUMP=$(git diff -U0 "$TARGET_BRANCH" -- "$PKGJSON" | grep -c '"version":')
  if [ $IS_PRIVATE_PKG != "true" ] &&  [ "$HAS_PKG_BUMP" -ne 0 ] ; then
    FROM_VERSION=$(git diff "$TARGET_BRANCH" -- "$PKGJSON" | grep '^-.*"version":' | sed -e 's/^.*version": "//' -e 's/[",]//g')
    TO_VERSION=$(git diff "$TARGET_BRANCH" -- "$PKGJSON" | grep '^\+.*"version":' | sed -e 's/^.*": "//' -e 's/[",]//g')

    # Ensuring that the version change is the only change in package.json
    PKG_JSON_OTHER_CHANGES=$(git diff --numstat "$TARGET_BRANCH" -- "$PKGJSON" | cut -f 1)
    if [ "$PKG_JSON_OTHER_CHANGES" -ne 1 ] ; then
      echo "==================================================="
      echo "                Impure package bump"
      echo "==================================================="
      echo ""
      echo "Version change found in $MODULE/package.json"
      echo "However, other changes were found in package.json"
      echo ""
      echo "Version changed from $FROM_VERSION -> $TO_VERSION:"
      echo ""
      echo "git diff of package.json:"
      echo "=========================================="
      git diff "$TARGET_BRANCH" -- "$PKGJSON" >&2
      echo "=========================================="
      exit 3
    fi


    # checking that the only files changed are packages/*/package.json
    OTHER_FILES_CHANGED=$(git diff --name-only "$TARGET_BRANCH" | grep -c -v "packages/.*/package.json")
    if [ "$OTHER_FILES_CHANGED" -ne 0 ] ; then
      echo "==================================================="
      echo "                Impure package bump"
      echo "==================================================="
      echo ""
      echo "Version change found in $MODULE/package.json"
      echo "However, other files were also changed"
      echo ""
      echo "Version changed from $FROM_VERSION -> $TO_VERSION:"
      echo ""
      echo "List of all files changed:"
      echo "=========================================="
      git diff --name-only "$TARGET_BRANCH" >&2
      echo "=========================================="
      exit 4
    fi



    HAS_PURE_PKG_BUMP=true
    echo "$MODULE: Pure package bump. $FROM_VERSION -> $TO_VERSION"
  else
    echo "$MODULE: n/a"
  fi
done

echo ""
echo ""

if [[ $HAS_PURE_PKG_BUMP == "true" ]] ; then
 [[ "$GITHUB_ACTIONS" == "true" ]] && echo "::set-output name=ispurebump::true"
 echo "This is a pure package bump."
else
 echo "No packages were bumped."
fi
