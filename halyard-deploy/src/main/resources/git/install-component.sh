INSTALL_DIR=~/dev/hal-spinnaker

if [ ! -d $INSTALL_DIR ]; then
 mkdir -p $INSTALL_DIR
fi

pushd $INSTALL_DIR

ARTIFACT={%artifact%}
VERSION={%version%}

ORIGIN_URL=git@github.com:{%origin%}/${ARTIFACT}.git
UPSTREAM_URL=git@github.com:{%upstream%}/${ARTIFACT}.git

if [ ! -d $ARTIFACT ]; then
  git clone $ORIGIN_URL
fi

pushd $ARTIFACT

git remote set-url origin $ORIGIN_URL

set +e
git remote get-url upstream

if [ $? -ne 0 ]; then
  git remote add upstream $UPSTREAM_URL
else
  git remote set-url upstream $UPSTREAM_URL
fi
set -e

git fetch origin
git fetch upstream

git checkout $VERSION

popd
popd