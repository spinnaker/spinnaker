#!/bin/bash
#
# wrapper script around running the merge or close auto bump PR
# gradle tasks (ensures publishing is enabled and simplifies the
# CLI for this specific use case).
#
# intended for use by a kork updater on their local dev environment
# after a kork release build has completed and the PRs are ready
# for merging (not intended as a CI type of script)
#
# to use, you will need github.token set in your
# ~/.gradle/gradle.properties file or GITHUB_TOKEN present as an
# environment variable
#

SCRIPT_DIR=`dirname $0`

GRADLE="$SCRIPT_DIR/gradlew -b $SCRIPT_DIR/build.gradle -PenablePublishing=true"

if [[ ! -z ${GITHUB_TOKEN} ]]; then
  GRADLE="$GRADLE -Pgithub.token=$GITHUB_TOKEN"
fi

case $1 in
  merge)
    $GRADLE mergeAllAutoBumpPRs
    ;;

  close)
    $GRADLE closeAllAutoBumpPRs
    ;;

  *)
    echo "usage: $0 <merge|close>"
    echo "  merge - merge all mergeable kork autobump PRs"
    echo "  close - close all open kork autobump PRs"
esac
