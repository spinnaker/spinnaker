#!/bin/bash
# This script will build the project.

if [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
  echo -e "Skipping build (running unit tests only) for Pull Request #$TRAVIS_PULL_REQUEST => Branch [$TRAVIS_BRANCH]"
  source ~/.nvm/nvm.sh
  NODE_JS_VERSION=`node -e 'console.log(require("./package.json").engines.node.replace(/[^\d\.]/g, ""))'`;
  nvm use $NODE_JS_VERSION

  ./node_modules/.bin/karma start --single-run

elif [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" == "" ]; then
  echo -e 'Build Branch with Snapshot => Branch ['$TRAVIS_BRANCH']'
  ./gradlew -Prelease.travisci=true -PbintrayUser="${bintrayUser}" -PbintrayKey="${bintrayKey}" build snapshot --stacktrace

elif [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" != "" ]; then
  echo -e 'Build Branch for Release => Branch ['$TRAVIS_BRANCH']  Tag ['$TRAVIS_TAG']'
  case "$TRAVIS_TAG" in
  version-*)
    ;; # Ignore Spinnaker product release tags.
  *-rc\.*)
    ./gradlew -PdeckVersion="${TRAVIS_TAG}" -Prelease.travisci=true -Prelease.useLastTag=true -PbintrayUser="${bintrayUser}" -PbintrayKey="${bintrayKey}" candidate --stacktrace
    ;;
  *)
    ./gradlew -PdeckVersion="${TRAVIS_TAG}" -Prelease.travisci=true -Prelease.useLastTag=true -PbintrayUser="${bintrayUser}" -PbintrayKey="${bintrayKey}" final --stacktrace
    ;;
  esac

else
  echo -e 'WARN: Should not be here => Branch ['$TRAVIS_BRANCH']  Tag ['$TRAVIS_TAG']  Pull Request ['$TRAVIS_PULL_REQUEST']'
  ./gradlew -Prelease.useLastTag=true build

fi
