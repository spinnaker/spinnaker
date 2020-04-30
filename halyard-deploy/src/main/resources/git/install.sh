#!/usr/bin/env bash

## auto-generated git install file written by halyard

STARTUP_SCRIPTS=()

{%install-commands%}

for SCRIPT in "${STARTUP_SCRIPTS[@]}"; do
  $SCRIPT
done
