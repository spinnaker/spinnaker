#!/usr/bin/env bash

## auto-generated git install file written by halyard

echo_err() {
  echo "$@" 1>&2
}

set +e
java_version=$(java -version 2>&1 head -1)
set -e

if [[ "$java_version" != *1.8* ]]; then
  echo_err "You need the java jdk at version 1.8 to build & run Spinnaker. Please install it."
  exit 1;
fi

which git &> /dev/null

if [ $? -ne 0 ]; then
  echo_err "You need git to be installed & configured to build & run Spinnaker. Please install & configure it."
  exit 1;
fi

exit 0

{%install-commands%}
