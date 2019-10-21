#!/usr/bin/env bash


if [[ "$TRAVIS_PULL_REQUEST" != "false" ]] || [[ "$TRAVIS_TAG" != "" ]]; then
  echo -e "Build Pull Request #$TRAVIS_PULL_REQUEST => Branch [$TRAVIS_BRANCH]"
  go build -v
  go test -v ./...
else
  echo -e "Unknown build command for PR? ('$TRAVIS_PULL') Branch ['$TRAVIS_BRANCH']  Tag ['$TRAVIS_TAG']"
fi
