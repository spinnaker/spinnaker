#!/bin/bash

#-------------------------------------------------------------------------------------
# Wraps all git binary calls to the git binary inside gitea container,
# transferring any needed files and adjusting file paths between host and container.
#-------------------------------------------------------------------------------------

set -e

git_args=$@

# Change local paths for paths inside the container, and copy any needed files
function pre_process() {
  container_name=$(docker ps | grep "gitea/gitea" | awk '{print $1}')
  docker exec "$container_name" mkdir -p "$(pwd)"
  if [[ $1 == "clone" ]]; then
    docker exec -w "$(pwd)" "$container_name" rm -rf test
  elif [[ $1 == "archive" ]]; then
    {
      while [[ "$#" -gt 0 ]]; do
        case $1 in
        --output)
          dst_path=$2
          docker exec "$container_name" mkdir -p "$(dirname $dst_path)"
          shift
          ;;
        esac
        shift
      done
    } >/dev/null 2>&1
  fi

  docker exec -i "$container_name" mkdir -p "$BUILD_DIR/ssh"
  docker cp "$BUILD_DIR/ssh/id_rsa_test" "$container_name":"$BUILD_DIR/ssh/id_rsa_test"
  docker exec -i "$container_name" chmod 600 "$BUILD_DIR/ssh/id_rsa_test"

  docker cp "${BUILD_DIR}/ssh/known_hosts" "$container_name":"${BUILD_DIR}/ssh/known_hosts"

  if [[ -n "${SSH_ASKPASS}" ]] ; then
    docker exec -i "$container_name" mkdir -p "$(dirname "$SSH_ASKPASS")"
    docker cp "${SSH_ASKPASS}" "$container_name":"${SSH_ASKPASS}"
    docker exec -i "$container_name" chmod +x "${SSH_ASKPASS}"
  fi
}

function execute() {
  docker exec -w "$(pwd)" -i -e GIT_SSH_COMMAND -e SSH_ASKPASS -e SSH_KEY_PWD -e DISPLAY -e GIT_CURL_VERBOSE -e GIT_TRACE "$container_name" git $git_args
}

# Make any changes locally reflecting the changes done inside the container
function post_process() {
  if [[ $git_args =~ .*clone.* ]]; then
    docker cp "$container_name":"$(pwd)/test" .
  elif [[ $git_args =~ .*pull.* ]]; then
    docker cp "$container_name":"$(pwd)" ..
  elif [[ $git_args =~ .*archive.* ]]; then
    docker cp "$container_name":"$dst_path" "$dst_path"
  fi
}

pre_process $@
execute
post_process
