#!/usr/bin/env bash

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)

. "$SCRIPT_DIR/gha_common.sh"

# navigate to project directory root
cd "$SCRIPT_DIR/.."

# bump versions of packages
npx lerna version --yes --no-push --conventional-commits -m $PACKAGE_BUMP_COMMIT_MSG
if [[ $(git rev-list --count @{u}..HEAD) -ne 0 ]] ; then
  # Synchronize @spinnaker/pluginsdk-peerdeps
  cd packages/pluginsdk-peerdeps
  yarn sync

  # navigate back to root
  cd ../..

  # perform any updates to yarn.lock
  yarn

  # commit changes
  git add yarn.lock packages/pluginsdk-peerdeps
  git commit -m "feat(peerdep-sync): Synchronize peerdependencies"

  # bump version of @spinnaker/pluginsdk-peerdeps
  npx lerna version --yes --no-push --conventional-commits -m $PEERDEP_BUMP_COMMIT_MSG
fi
