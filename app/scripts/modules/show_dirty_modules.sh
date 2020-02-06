#!/bin/bash
# Reports if any modules are dirty, and what commits have been made since the last package.json commit
cd "$(dirname "$0")" || exit 1;

for PKGJSON in */package.json ; do
  MODULE=$(dirname "$PKGJSON");

  COUNT=$(./show_unpublished_commits.sh "$MODULE" | wc -l | sed -e 's/ //g')
  if [ "$COUNT" -ne 0 ] ; then
    echo ""
    echo ""
    echo "=== $MODULE is dirty ($COUNT commits) ===";
    ./show_unpublished_commits.sh "$MODULE"
  fi
done

