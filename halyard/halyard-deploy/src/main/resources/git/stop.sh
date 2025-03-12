#!/usr/bin/env bash

GIT_ROOT={%git-root%}
ARTIFACT={%artifact%}

PID_FILE=${GIT_ROOT}/${ARTIFACT}.pid
ARTIFACT_DIR=${GIT_ROOT}/${ARTIFACT}

function try_stop() {
  if [ ! -f "$PID_FILE" ]; then
    echo "$ARTIFACT does not seem to be running..."
    return
  fi

  PID=$(cat $PID_FILE)

  set +e
  kill $PID
  set -e

  rm $PID_FILE
}

echo "Stopping ${ARTIFACT}..."

try_stop
