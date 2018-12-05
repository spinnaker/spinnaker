#!/usr/bin/env bash

set -x
set -e

# Update deck to latest master branch, build production assets & execute functional tests

# This file is intended to be run inside a built deck-functional-tests container

cd /
/update-everything.sh
cd /deck-functional-tests
/run-tests.sh
