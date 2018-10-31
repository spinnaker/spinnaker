#!/bin/bash
if [ "$1" == "" ] ; then
  echo "Bump versions in package.json and (optionally) publish deck modules";
  echo "$0 <module1> <module2>";
  exit
fi

CWD=`pwd`;

for i in $* ; do
  if [ ! -d $i ] ; then
    echo "$CWD/$i does not exist";
    exit;
  fi
done

CURRENTBRANCH=`git branch | grep \* | cut -d ' ' -f2`;
if [ "$CURRENTBRANCH" == "master" ] ; then
  echo "";
  read -p "Create new branch for package bumps? (y/n) " -n 1 -r
  echo "";
  if [ "$REPLY" == "y" ] ; then
    COUNT=;
    while git show-ref --verify --quiet refs/heads/package-bump$COUNT ; do
      let COUNT=COUNT+1;
    done
    git co -b package-bump$COUNT;
  fi
fi
CURRENTBRANCH=`git branch | grep \* | cut -d ' ' -f2`;

for i in $* ; do
  COMMITS=`echo "" && echo "" && ./show_unpublished_commits.sh $i`;
  pushd $i > /dev/null;
  npm version patch --no-git-tag-version;
  VERSION=`node -e 'console.log(JSON.parse(require("fs").readFileSync("package.json")).version)'`;
  PACKAGE=`node -e 'console.log(JSON.parse(require("fs").readFileSync("package.json")).name)'`;

  git commit -m "chore($i): Bump version to ${VERSION}${COMMITS}" package.json

  echo "";
  read -p "Publish ${PACKAGE}? (y/n) " -n 1 -r
  echo "";
  if [ "$REPLY" == "y" ] ; then
    npm publish;
  fi

  popd > /dev/null;
done


if [ "$CURRENTBRANCH" != "master" ] ; then
  echo "";
  read -p "Push current branch ${CURRENTBRANCH} to 'origin'? (y/n) " -n 1 -r
  echo "";
  if [ "$REPLY" == "y" ] ; then
    git push origin $CURRENTBRANCH;
  fi
fi
