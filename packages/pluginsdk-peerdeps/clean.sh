#!/usr/bin/env bash
set -e
[[ -e .scaffolddir ]] || {
  echo Nothing to clean...
  exit 0
}

SCAFFOLDDIR=$(cat .scaffolddir)

[[ -d ${SCAFFOLDDIR} ]] && {
  echo "Cleaning ${SCAFFOLDDIR}"
  rm -rf ${SCAFFOLDDIR}
}

rm .scaffolddir
