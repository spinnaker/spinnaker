#!/bin/bash

beginGroup() {
  if [[ -n "$GITHUB_ACTIONS" ]] ; then
    echo "::group::$*"
  elif [[ -n "$TRAVIS" ]] ; then
    echo "travis_fold:start:$*"
    echo "::endgroup::"
  fi
}

endGroup() {
  if [[ -n "$GITHUB_ACTIONS" ]] ; then
    echo "::endgroup::"
  elif [[ -n "$TRAVIS" ]] ; then
    echo "travis_fold:end:$*"
  fi
}

cd "$(dirname "$0")" || exit $?
BUILD_ORDER=$(./build_order.sh $*)
[[ $? -eq 0 ]] || exit $?

for PACKAGE in $BUILD_ORDER ; do
  beginGroup "$PACKAGE"
  pushd "$PACKAGE" || exit $?
  yarn lib || exit 255
  endGroup "$PACKAGE"
  popd || exit $?
done
