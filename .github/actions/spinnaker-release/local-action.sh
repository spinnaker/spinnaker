#!/usr/bin/env bash

# To test publishing, requires a GAR JSON credential at: "$HOME/monorepo/spinnaker-monorepo-test-gar.json"

# Set action defaults
export INPUT_DRY_RUN="${INPUT_DRY_RUN:-true}"
export INPUT_ADD_TO_VERSIONS_YML="${INPUT_ADD_TO_VERSIONS_YML:-true}"
export INPUT_PUBLISH_BOM="${INPUT_PUBLISH_BOM:-true}"
export INPUT_PUBLISH_CHANGELOG="${INPUT_PUBLISH_CHANGELOG:-true}"
CJSON_FILE_PATH="${CJSON_FILE_PATH:-$HOME/monorepo/spinnaker-monorepo-test-gar.json}"
cjson=$(cat "$HOME/monorepo/spinnaker-monorepo-test-gar.json")
export INPUT_CREDENTIALS_JSON="$cjson"

export INPUT_BUCKET='spinnaker-monorepo-test'
export INPUT_BOM_BUCKET_PATH='boms'
export INPUT_VERSIONS_YML_BUCKET_PATH=''

export INPUT_AS_DEBIAN_REPOSITORY='https://us-west2-docker.pkg.dev/spinnaker-monorepo-test/apt'
export INPUT_AS_DOCKER_REGISTRY='https://us-west2-docker.pkg.dev/spinnaker-monorepo-test/docker'
export INPUT_AS_GIT_PREFIX='https://github.com/jcavanagh/spinnaker-monorepo-public'
export INPUT_AS_GOOGLE_IMAGE_PROJECT='https://us-west2-docker.pkg.dev/spinnaker-monorepo-test/docker'

export INPUT_DEP_CONSUL_VERSION='0.7.5'
export INPUT_DEP_REDIS_VERSION='2:2.8.4-2'
export INPUT_DEP_VAULT_VERSION='0.7.0'

export INPUT_VERSION="1.37.8"

export INPUT_GITHUB_PAT=$(cat "$HOME/monorepo/spinnaker-monorepo-public-pat")
export INPUT_MONOREPO_LOCATION='jcavanagh/spinnaker-monorepo-public'
export INPUT_DOCS_REPO_LOCATION='jcavanagh/spinnaker.io'
export INPUT_GIT_EMAIL=spinbot@spinnaker.io
export INPUT_GIT_NAME=spinnakerbot

npm run build
node ./dist/index.js
