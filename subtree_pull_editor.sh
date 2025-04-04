#!/usr/bin/env bash
# This script is designed to be used as an "editor" for subtree pull commits, to make them a little more informative
# By default, subtree creates a message with just the MERGE_HEAD, which is not helpful to humans
# GIT_EDITOR=./path/to/this.sh git subtree pull ...

OUTFILE="$1"

# Ensure that our subtree context is set so the commit message isn't so opaque
if [[ -z $GIT_SUBTREE ]] || [[ -z $GIT_SUBTREE_REMOTE ]]; then
  echo "GIT_SUBTREE and GIT_SUBTREE_REMOTE must be set to generate a useful subtree commit message"
  exit 1
fi

if [[ ! -f .git/MERGE_HEAD ]]; then
  echo "No .git/MERGE_HEAD file - cannot continue"
  exit 1
fi

MERGE_HEAD=$(tr -d "\n" < .git/MERGE_HEAD)
echo "Merge $MERGE_HEAD into $GIT_SUBTREE" > "$OUTFILE"
echo "" >> "$OUTFILE"
echo "From remote $GIT_SUBTREE_REMOTE" >> "$OUTFILE"
echo "" >> "$OUTFILE"
echo "Commits added:" >> "$OUTFILE"

# Add in all commits reachable from MERGE_HEAD and not reachable from HEAD
git --no-pager log "$MERGE_HEAD" ^HEAD --oneline --no-decorate --no-color >> "$OUTFILE"
