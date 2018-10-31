#!/bin/bash

for i in `ls */package.json` ; do 
  DIR=`dirname $i`;
  pushd $DIR > /dev/null;
  git log -1 --name-only . | grep "$DIR/package.json" > /dev/null || echo $DIR is dirty;

  popd > /dev/null;
done
