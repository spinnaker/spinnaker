#!/usr/bin/env bash

shortSha=$(echo $COMMIT_SHA | head -c 10)

# Commit messages to be used between steps
export PACKAGE_BUMP_COMMIT_MSG="chore(publish): publish packages ($shortSha) - run: $RUN_NUMBER attempt: $RUN_ATTEMPT"
export PEERDEP_BUMP_COMMIT_MSG="chore(publish): publish peerdeps ($shortSha) - run: $RUN_NUMBER attempt: $RUN_ATTEMPT"

# Default to empty hashes
export PACKAGE_BUMP_COMMIT_HASH=""
export PEERDEP_BUMP_COMMIT_HASH=""

function updateBumpHashes() {
  export PACKAGE_BUMP_COMMIT_HASH=$(git log -1 --grep=$PACKAGE_BUMP_COMMIT_MSG --format=%H)
  export PEERDEP_BUMP_COMMIT_HASH=$(git log -1 --grep=$PEERDEP_BUMP_COMMIT_MSG --format=%H)
}
