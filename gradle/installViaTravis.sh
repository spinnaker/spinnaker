#!/bin/bash
# This script will build the project.

GRADLE="./gradlew --no-daemon --max-workers=1"
export GRADLE_OPTS="-Xmx1g -Xms1g"

if [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
  echo -e "Assemble Pull Request #$TRAVIS_PULL_REQUEST => Branch [$TRAVIS_BRANCH]"
  $GRADLE assemble
elif [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" == "" ]; then
  echo -e 'Assemble Branch with Snapshot => Branch ['$TRAVIS_BRANCH']'
  $GRADLE -Prelease.travisci=true -x test assemble
elif [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" != "" ]; then
  echo -e 'Assemble Branch for Release => Branch ['$TRAVIS_BRANCH']  Tag ['$TRAVIS_TAG']'
  $GRADLE -Prelease.travisci=true -Prelease.useLastTag=true -x test assemble
else
  echo -e 'WARN: Should not be here => Branch ['$TRAVIS_BRANCH']  Tag ['$TRAVIS_TAG']  Pull Request ['$TRAVIS_PULL_REQUEST']'
  $GRADLE assemble
fi

