#!/usr/bin/env bash

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)

# navigate to project directory root
cd "$SCRIPT_DIR/.."

# Gets any updates to the HANGELOG.md files in packages/* from the last commit
function changelog() {
  hash=$1

  git diff -u "$hash" packages/*/CHANGELOG.md  | \
    # Only process added or changed lines
    grep "^\+" | \
    # Remove the leading + characters from 'diff -u'
    sed -e 's/^\+//' | \
    # Create a markdown heading out of the git diff filename and the heading from the changelog
    ## core [1.0.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.9.0...@spinnaker/core@1.0.0) (2021-07-16)
    #
    # Join the git diff line starting with ++ with the next line
    sed -e '/^\+\+/ N; s/\n//g' |  \
    # Extract the package name and move it after the heading markdown (##)
    sed -e 's/^\+\+.*\/packages\/\(.*\)\/CHANGELOG.md\(#*\)/\2 \1/'
}

if [ ! -z "$PACKAGE_BUMP_COMMIT_HASH" ]; then
  CHANGELOG=$(changelog "$PACKAGE_BUMP_COMMIT_HASH")

  if [ ! -z "$PEERDEP_BUMP_COMMIT_HASH" ]; then
    CHANGELOG+=$(changelog "$PEERDEP_BUMP_COMMIT_HASH")
  fi

  # Quote newlines so the entire thing can be emitted as a single line output
  CHANGELOG="${CHANGELOG//'%'/'%25'}"
  CHANGELOG="${CHANGELOG//$'\n'/'%0A'}"
  CHANGELOG="${CHANGELOG//$'\r'/'%0D'}"

  ## Github Action Step output variable: changelog
  echo ::set-output name=changelog::${CHANGELOG}
fi
