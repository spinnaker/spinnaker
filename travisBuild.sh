#!/usr/bin/env bash


if [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
  echo -e "Build Pull Request #$TRAVIS_PULL_REQUEST => Branch [$TRAVIS_BRANCH]"
  go build -v
  go test -v ./...
elif [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" != "" ]; then
  echo -e 'Build Branch for Release => Branch ['$TRAVIS_BRANCH']  Tag ['$TRAVIS_TAG']'
  openssl aes-256-cbc -K $encrypted_ce4fc3e4b052_key -iv $encrypted_ce4fc3e4b052_iv -in key.json.enc -out key.json -d
  ./release.sh --version $TRAVIS_TAG --key_file key.json
else
  echo -e 'Unknown build command for PR? ('$TRAVIS_PULL') Branch ['$TRAVIS_BRANCH']  Tag ['$TRAVIS_TAG']'
fi

