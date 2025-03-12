#!/usr/bin/env bash

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)

# navigate to project directory root
cd "$SCRIPT_DIR/.."

packageBumpCommitMsg="chore(publish): publish packages ($COMMIT_SHA)"
peerdepBumpCommitMsg="chore(publish): publish peerdeps ($COMMIT_SHA)"

# bump versions of packages
npx lerna version --yes --no-push --conventional-commits -m "$packageBumpCommitMsg"
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
  npx lerna version --yes --no-push --conventional-commits -m "$peerdepBumpCommitMsg"

  packageBumpCommitHash=$(git log -1 --grep="$packageBumpCommitMsg" --format=%H)
  peerdepBumpCommitHash=$(git log -1 --grep="$peerdepBumpCommitMsg" --format=%H)

  echo ::set-output name=packageBumpCommitHash::${packageBumpCommitHash}
  echo ::set-output name=peerdepBumpCommitHash::${peerdepBumpCommitHash}
fi
