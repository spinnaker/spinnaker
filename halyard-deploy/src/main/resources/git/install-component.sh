#!/usr/bin/env bash

ARTIFACT={%artifact%}
SCRIPTS_DIR={%scripts-dir%}

STARTUP_SCRIPTS+=(${SCRIPTS_DIR}/${ARTIFACT}-start.sh)

