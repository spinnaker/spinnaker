## TODO(lwander): make configurable
GIT_ROOT={%git-root%}

if [ ! -d $GIT_ROOT ]; then
 mkdir -p $GIT_ROOT
fi

pushd $GIT_ROOT

ARTIFACT={%artifact%}
VERSION={%version%}
REPO={%repo%}
UPDATE={%update%}

## TODO(lwander): make configurable
ORIGIN_URL=git@github.com:{%origin%}/${REPO}.git
UPSTREAM_URL=git@github.com:{%upstream%}/${REPO}.git

if [ ! -d $ARTIFACT ]; then
  git clone $ORIGIN_URL $ARTIFACT
fi

pushd $ARTIFACT

git remote set-url origin $ORIGIN_URL

set +e
git remote get-url upstream 2> /dev/null
ERR=$?

if [ "$ERR" -ne 0 ]; then
  git remote remove upstream 2> /dev/null
  git remote add upstream $UPSTREAM_URL
else
  git remote set-url upstream $UPSTREAM_URL
fi
set -e

if [ -n "$UPDATE" ]; then
  git fetch origin
  git fetch upstream

  DIRTY=$(git_changes)

  if [ -n "$DIRTY" ]; then
    echo "Stashing changes to $ARTIFACT"
    git stash
  else
    echo "No changes to stash in $ARTIFACT"
  fi

  git checkout $VERSION

  if [ -n "$DIRTY" ]; then
    set +e
    git stash apply
    ERR=$?

    if [ "$ERR" -ne 0 ]; then
      failed_stash $ARTIFACT
    fi

    set -e
  fi
fi

popd
popd