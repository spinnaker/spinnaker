#!/bin/bash
cd "$(dirname "$0")" || exit 1;

MODULE=$1;
if [ "$MODULE" == "" ] ; then
  echo "Shows what commits have been made since the last package.json commit";
  echo "usage:   $0 <module>";
  echo "example: $0 core";
  exit 2;
fi

pushd "$MODULE" > /dev/null || exit 3;
LASTBUMP=$(git log -1 package.json | grep commit | sed -e 's/commit //');
git --no-pager log --pretty=oneline "${LASTBUMP}..HEAD" .;

popd > /dev/null || exit 4;
