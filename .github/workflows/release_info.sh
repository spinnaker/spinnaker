#!/bin/bash -x

# Only look to the latest release to determine the previous tag -- this allows us to skip unsupported tag formats (like `version-1.0.0`)
export PREVIOUS_TAG=`curl --silent "https://api.github.com/repos/jonsie/$1/releases/latest" | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/'`
echo "PREVIOUS_TAG=$PREVIOUS_TAG"
export NEW_TAG=${GITHUB_REF/refs\/tags\//}
echo "NEW_TAG=$NEW_TAG"
export CHANGELOG=`git log $NEW_TAG...$PREVIOUS_TAG --oneline`
echo "CHANGELOG=$CHANGELOG"

#Format the changelog so it's markdown compatible
CHANGELOG="${CHANGELOG//$'%'/%25}"
CHANGELOG="${CHANGELOG//$'\n'/%0A}"
CHANGELOG="${CHANGELOG//$'\r'/%0D}"

# If the previous release tag is the same as this tag the user likely cut a release (and in the process created a tag), which means we can skip the need to create a release
export SKIP_RELEASE=`[[ "$PREVIOUS_TAG" = "$NEW_TAG" ]] && echo "true" || echo "false"`
