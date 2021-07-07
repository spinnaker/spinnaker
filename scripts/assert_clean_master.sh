#!/bin/bash
# Runs this script from the 'deck/scripts' directory
cd "$(dirname "$0")" || exit;
pushd ../

# Assert that we are on the 'master' branch
# (a local branch that tracks decks 'master' branch)
CURRENTBRANCH=$(git symbolic-ref --short HEAD)
TRACKING=$(git rev-parse --abbrev-ref --symbolic-full-name "@{u}")
function error() {
  echo "$*" >&2
}

if [ $? -ne 0 ] ; then
  error "You must be on 'master' (a branch tracking the upstream Deck 'master' branch)."
  error
  error "The current branch '$CURRENTBRANCH' is not tracking any remote."
  exit 1
fi

REMOTE=$(echo "$TRACKING" | sed -e 's/[/].*//')
BRANCH=$(echo "$TRACKING" | sed -e 's/[^/]*[/]//')
REMOTE_URL=$(git remote get-url "${REMOTE}")
DECK_HTTPS="https://github.com/spinnaker/deck.git"
DECK_GIT="git@github.com:spinnaker/deck.git"

if [ "x${REMOTE_URL}" != "x${DECK_HTTPS}" ] && [ "x${REMOTE_URL}" != "x${DECK_GIT}" ] ; then
  error "You must be on 'master' (a branch tracking the upstream Deck 'master' branch)."
  error
  error "The current branch '${CURRENTBRANCH}' is tracking '${TRACKING}'."
  error "However, the remote '${REMOTE}' has a URL of '${REMOTE_URL}'."
  error "The tracked remote should be either '${DECK_HTTPS}' or '${DECK_GIT}'."
  exit 2
fi

if [ "x${BRANCH}" != "xmaster" ] ; then
  error "You must be on 'master' (a branch tracking the upstream Deck 'master' branch)."
  error
  error "The current branch '${CURRENTBRANCH}' is tracking '${TRACKING}'."
  exit 3
fi

# Assert that the local branch is "clean" so we don't publish any uncommitted code
DIRTY_COMMITS=$(git status --porcelain);
if [ "x${DIRTY_COMMITS}" != "x" ] ; then
  error "Your working copy is not clean (you have uncommited changes)."
  error
  error "Please stash these changes before publishing using 'git stash'.";
  error
  git status --porcelain
  exit 4;
fi

# Get the latest information from the remote
git fetch "$REMOTE"

STATUS=$(git status -sb --porcelain)
if [[ ${STATUS} =~ [Bb]ehind ]] ; then
  error "Your local branch is behind the upstream branch '${TRACKING}'."
  error
  error "The upstream branch has additional commits that would not be included in the published package."
  error "Run 'git pull' to update your local branch."
  error
  git status -sb
  exit 5
fi

if [[ ${STATUS} =~ [Aa]head ]] ; then
  error "Your local branch is ahead of the upstream branch '${TRACKING}'."
  error
  error "Your local branch has additional commits that are not reflected in the upstream branch."
  error "Add your commits to the upstream branch by creating a pull request."
  error
  git status -sb
  exit 6
fi
