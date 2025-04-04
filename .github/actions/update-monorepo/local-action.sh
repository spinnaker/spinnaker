#!/usr/bin/env bash

# Set action defaults
export INPUT_REMOTE_REF="${INPUT_REMOTE_REF:-master}"
export INPUT_REPOS="${INPUT_REPOS:-clouddriver,deck,deck-kayenta,echo,fiat,front50,gate,halyard,igor,kayenta,keel,orca,rosco,spin,spinnaker-gradle-project}"

npm run build
node ./dist/index.js
