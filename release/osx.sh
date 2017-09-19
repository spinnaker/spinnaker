#!/usr/bin/env bash

set -e

tar -cvf halyard.tar.gz -C halyard-web/build/install halyard/ -C ../../../startup/osx hal update-halyard
