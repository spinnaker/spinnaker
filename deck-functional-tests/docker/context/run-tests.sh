#!/usr/bin/env bash

set -x
set -e

# Update deck to latest master branch, build production assets & execute functional tests

# This file is intended to be run inside a built deck-functional-tests container

xvfb-run yarn test --replay-network --serve-static /deck/build/webpack
