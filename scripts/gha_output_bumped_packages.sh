#!/usr/bin/env bash

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)

. "$SCRIPT_DIR/gha_common.sh"

# navigate to project directory root
cd "$SCRIPT_DIR/.."

updateBumpHashes()

if [ ! -z "$PACKAGE_BUMP_COMMIT_HASH" ]; then
  BUMPS=$(git log -1 $PACKAGE_BUMP_COMMIT_HASH --pretty=%B | grep "^ - " | sed -e 's/^ - //' -e 's/^@spinnaker\///');

  if [ ! -z "$PEERDEP_BUMP_COMMIT_HASH" ]; then
    BUMPS+=$(git log -1 $PEERDEP_BUMP_COMMIT_HASH --pretty=%B | grep "^ - " | sed -e 's/^ - //' -e 's/^@spinnaker\///');
  fi

  echo ::set-output name=bumps::${BUMPS}
fi
