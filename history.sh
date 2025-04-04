#!/usr/bin/env bash

# Usage: ./history.sh <subtree>/<file_path> or ./history.sh <subtree>
# TODO: Passthrough arguments to `git log`

FILE_PATH=${1#*/}
SUBTREE=${1%%/*}

# Find the meta commit where the subtree was added, and find the parent of the OSS merge tree (aka the "split")
SPLIT_COMMIT_MSG=$(git log --grep "git-subtree-dir: $SUBTREE$" | grep "git-subtree-split: ")
SUBTREE_SPLIT_SHA=${SPLIT_COMMIT_MSG#*: }

# If the user wants the whole subtree, use dot to make git happy
FILE_PATH="${FILE_PATH:-.}"

# Output the combined log
(git log --color=always HEAD -- "$SUBTREE/$FILE_PATH" && echo && git log --color=always "$SUBTREE_SPLIT_SHA" -- "$FILE_PATH") | less
