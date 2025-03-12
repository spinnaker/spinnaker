#!/bin/bash -x

NEW_TAG=${GITHUB_REF/refs\/tags\//}
export NEW_TAG
echo "NEW_TAG=$NEW_TAG"
# Glob match previous tags which should be format v1.2.3. Avoids Deck's npm tagging.
PREVIOUS_TAG=$(git describe --abbrev=0 --tags "${NEW_TAG}"^ --match 'v[0-9]*')
export PREVIOUS_TAG
echo "PREVIOUS_TAG=$PREVIOUS_TAG"
CHANGELOG=$(git log "$NEW_TAG"..."$PREVIOUS_TAG" --oneline)
export CHANGELOG
echo "CHANGELOG=$CHANGELOG"

# Format the changelog so it's markdown compatible
CHANGELOG="${CHANGELOG//$'%'/%25}"
CHANGELOG="${CHANGELOG//$'\n'/%0A}"
CHANGELOG="${CHANGELOG//$'\r'/%0D}"

# If the previous release tag is the same as this tag the user likely cut a release (and in the process created a tag), which means we can skip the need to create a release
SKIP_RELEASE=$([[ "$PREVIOUS_TAG" = "$NEW_TAG" ]] && echo "true" || echo "false")
export SKIP_RELEASE

# https://github.com/fsaintjacques/semver-tool/blob/master/src/semver#L5-L14
NAT='0|[1-9][0-9]*'
ALPHANUM='[0-9]*[A-Za-z-][0-9A-Za-z-]*'
IDENT="$NAT|$ALPHANUM"
FIELD='[0-9A-Za-z-]+'
SEMVER_REGEX="\
^[vV]?\
($NAT)\\.($NAT)\\.($NAT)\
(\\-(${IDENT})(\\.(${IDENT}))*)?\
(\\+${FIELD}(\\.${FIELD})*)?$"

# Used in downstream steps to determine if the release should be marked as a "prerelease" and if the build should build candidate release artifacts
IS_CANDIDATE=$([[ $NEW_TAG =~ $SEMVER_REGEX && -n ${BASH_REMATCH[4]} ]] && echo "true" || echo "false")
export IS_CANDIDATE

# This is the version string we will pass to the build, trim off leading 'v' if present
RELEASE_VERSION=$([[ $NEW_TAG =~ $SEMVER_REGEX ]] && echo "${NEW_TAG:1}" || echo "${NEW_TAG}")
export RELEASE_VERSION
echo "RELEASE_VERSION=$RELEASE_VERSION"
