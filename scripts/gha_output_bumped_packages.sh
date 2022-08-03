#!/usr/bin/env bash

if [ ! -z "$PACKAGE_BUMP_COMMIT_HASH" ]; then
  BUMPS=$(git log -1 "$PACKAGE_BUMP_COMMIT_HASH" --pretty=%B | grep "^ - " | sed -e 's/^ - //' -e 's/^@spinnaker\///');

  if [ ! -z "$PEERDEP_BUMP_COMMIT_HASH" ]; then
    BUMPS+=$(git log -1 "$PEERDEP_BUMP_COMMIT_HASH" --pretty=%B | grep "^ - " | sed -e 's/^ - //' -e 's/^@spinnaker\///');
  fi

  echo ::set-output name=bumps::${BUMPS}
fi
