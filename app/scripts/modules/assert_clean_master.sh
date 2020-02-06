#!/bin/bash
# Runs this script from the 'deck/app/scripts/modules' directory
cd "$(dirname "$0")" || exit;

# Assert that we are on the 'master' branch
# (a local branch that tracks decks 'master' branch)
CURRENTBRANCH=$(git symbolic-ref --short HEAD)
TRACKING=$(git rev-parse --abbrev-ref --symbolic-full-name "@{u}")

if [ $? -ne 0 ] ; then
  echo "You must be on 'master' (a branch tracking the upstream Deck 'master' branch)."
  echo
  echo "The current branch '$CURRENTBRANCH' is not tracking any remote."
  exit 1
fi

REMOTE=$(echo "$TRACKING" | sed -e 's/[/].*//')
BRANCH=$(echo "$TRACKING" | sed -e 's/[^/]*[/]//')
REMOTE_URL=$(git remote get-url "${REMOTE}")
DECK_HTTPS="https://github.com/spinnaker/deck.git"
DECK_GIT="git@github.com:spinnaker/deck.git"

if [ "x${REMOTE_URL}" != "x${DECK_HTTPS}" ] && [ "x${REMOTE_URL}" != "x${DECK_GIT}" ] ; then
  echo "You must be on 'master' (a branch tracking the upstream Deck 'master' branch)."
  echo
  echo "The current branch '${CURRENTBRANCH}' is tracking '${TRACKING}'."
  echo "However, the remote '${REMOTE}' has a URL of '${REMOTE_URL}'."
  echo "The tracked remote should be either '${DECK_HTTPS}' or '${DECK_GIT}'."
  exit 2
fi

if [ "x${BRANCH}" != "xmaster" ] ; then
  echo "You must be on 'master' (a branch tracking the upstream Deck 'master' branch)."
  echo
  echo "The current branch '${CURRENTBRANCH}' is tracking '${TRACKING}'."
  exit 3
fi

# Assert that the local branch is "clean" so we don't publish any uncommitted code
DIRTY_COMMITS=$(git status --porcelain);
if [ "x${DIRTY_COMMITS}" != "x" ] ; then
  echo "Your working copy is not clean (you have uncommited changes)."
  echo
  echo "Please stash these changes before publishing using 'git stash'.";
  echo
  git status --porcelain
  exit 4;
fi

# Get the latest information from the remote
git fetch "$REMOTE"

STATUS=$(git status -sb --porcelain)
if [[ ${STATUS} =~ [Bb]ehind ]] ; then
  echo "Your local branch is behind the upstream branch '${TRACKING}'."
  echo
  echo "The upstream branch has additional commits that would not be included in the published package."
  echo "Run 'git pull' to update your local branch."
  echo
  git status -sb
  exit 5
fi

if [[ ${STATUS} =~ [Aa]head ]] ; then
  echo "Your local branch is ahead of the upstream branch '${TRACKING}'."
  echo
  echo "Your local branch has additional commits that are not reflected in the upstream branch."
  echo "Add your commits to the upstream branch by creating a pull request."
  echo
  git status -sb
  exit 6
fi
