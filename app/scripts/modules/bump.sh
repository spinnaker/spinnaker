#!/bin/bash
if [ "$1" == "" ] ; then
  echo "$0 <module1> <module2>";
  exit
fi

for i in $* ; do 
  pushd $i > /dev/null;
  npm version patch --no-git-tag-version;
  VERSION=`node -e 'console.log(JSON.parse(require("fs").readFileSync("package.json")).version)'`;
  git ci -m "chore($i): Bump version to ${VERSION}" package.json
  popd > /dev/null;
done
