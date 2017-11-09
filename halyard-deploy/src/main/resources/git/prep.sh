#!/usr/bin/env bash

## auto-generated git prep file written by halyard

which git &> /dev/null

if [ $? -ne 0 ]; then
  echo_err "You need git to be installed & configured to build & run Spinnaker. Please install & configure it."
  exit 1;
fi

{%prep-commands%}
