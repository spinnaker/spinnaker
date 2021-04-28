#!/usr/bin/env bash

set -ex
set -o pipefail

BUILD_ARGS="$*"

/etc/init.d/mysql restart

echo "starting gradle job"
if [[ -z "${BUILD_ARGS// }" ]]
then
  echo "no argument received. running default build"
  ./gradlew --no-daemon -PbuildingInDocker=true keel-web:installDist -x test
else
  echo "running ${BUILD_ARGS}"
  ${BUILD_ARGS}
fi
