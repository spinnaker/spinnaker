#!/bin/bash
# Reports if any modules are dirty, and what commits have been made since the last package.json commit
cd "$(dirname "$0")" || exit 1;
pushd ../

MODULES_DIR="packages"

for PKGJSON in $MODULES_DIR/*/package.json ; do
  MODULE=$(dirname "$PKGJSON");

  COUNT=$(./scripts/show_changelog.sh "$MODULE/package.json" | wc -l | sed -e 's/ //g')
  if [ "$COUNT" -ne 0 ] ; then
    echo ""
    echo ""
    echo "=== $MODULE is dirty ($COUNT commits) ===";
    ./scripts/show_changelog.sh "$MODULE/package.json"
  fi
done

