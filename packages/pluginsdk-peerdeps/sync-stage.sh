#!/usr/bin/env bash
# This script creates a temporary directory and scaffolds a new plugin.
# It updates that scaffolded plugin's regular dependencies using the sync-deck.js script.

set -e

SCRIPTDIR=$(cd "$(dirname "$0")"; pwd -P)

cd $(mktemp -d)
SCAFFOLDDIR=${PWD}

echo "${SCAFFOLDDIR}" > "${SCRIPTDIR}/.scaffolddir"

echo "pluginsdk-peerdeps: Scaffolding a new plugin into $SCAFFOLDDIR"
"$SCRIPTDIR"/../pluginsdk/scripts/scaffold.js --directory . --name scaffold
echo "[pluginsdk-peerdeps] New plugin scaffolded into $SCAFFOLDDIR"
echo "node_modules" > .gitignore
git init
git add .
git commit -m "initial commit"
node "$SCRIPTDIR"/sync-deck.js
yarn

echo ""
echo ""
echo ""
echo ""
echo "---------------------------------------------------------------------------"
echo ""
echo ""
echo ""
echo ""
echo "A plugin has been scaffolded into $SCAFFOLDDIR"
echo "Update the package.json and then run 'yarn sync' in this directory."
echo ""
echo "  👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇"
echo ""
echo "  cd ${SCAFFOLDDIR}"
echo "  yarn upgrade-interactive --latest"
echo "  git diff"
