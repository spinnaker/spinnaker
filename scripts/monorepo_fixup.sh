#!/usr/bin/env bash
# This script should be run when updating a developer's working copy to the monorepo codebase

rm -rf node_modules build packages/*/node_modules packages/*/dist packages/*/lib
yarn
yarn modules
