#!/usr/bin/env bash

## auto-generated git prep file written by halyard

which git &> /dev/null

function echo_err() {
  echo "$@" 1>&2
}

if [ $? -ne 0 ]; then
  echo_err "You need git to be installed & configured to build & run Spinnaker. Please install & configure it."
  exit 1;
fi

function git_changes {
  git diff --shortstat 2> /dev/null | tail -n1
}

FAILED_STASHES=()

function failed_stash {
  FAILED_STASHES+=($1)
}

{%prep-commands%}
