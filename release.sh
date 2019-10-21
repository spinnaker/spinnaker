#!/usr/bin/env bash

set -e
set -x

PROD_SPIN_GCS_BUCKET_PATH="gs://spinnaker-artifacts/spin"

KEY_FILE=
SPIN_GCS_BUCKET_PATH=
VERSION=


function process_args() {
  while [[ $# -gt 0 ]]
  do
    local key="$1"
    shift
    case $key in
      --gcs_bucket_path)
        SPIN_GCS_BUCKET_PATH="$1"
        shift
        ;;
      --key_file)
        KEY_FILE="$1"
        shift
        ;;
      --version)
        VERSION="$1"
        shift
        ;;
      --help|-help|-h)
        print_usage
        exit 0
        ;;
      *)
        echo "ERROR: Unknown argument '$key'"
        exit -1
    esac
  done
}


function print_usage() {
    cat <<EOF
usage: $0 --key_file <key file path> --version <version>
      [--gcs_bucket_path <path>]


    --gcs_bucket_path <arg>      Path to the GCS bucket to write the 'spin' binaries to.

    --key_file <arg>             Service account JSON key file to use to upload 'spin'
                                       binaries.

    --version <arg>              Version to tag the 'spin' binaries with.
EOF
}


process_args "$@"

CURR_DIR="$(pwd)"

# Google cloud sdk installation from
# https://cloud.google.com/sdk/docs/downloads-interactive.
if ! command -v gcloud > /dev/null; then
  curl https://sdk.cloud.google.com | bash -s -- --disable-prompts --install-dir="$CURR_DIR"
fi

export PATH=$PATH:$CURR_DIR/google-cloud-sdk/bin

if [[ -z "$KEY_FILE" ]]; then
  echo "No key file specified with --key_file, exiting"
  exit 1
fi

gcloud auth activate-service-account --key-file "${KEY_FILE}"
gcloud components install gsutil -q

if [[ -z "$VERSION" ]]; then
  echo -e "No version to release specified with --version, exiting"
  exit 1
fi

if [[ -z "$SPIN_GCS_BUCKET_PATH" ]]; then
  echo "No GCS bucket specified using ${PROD_SPIN_GCS_BUCKET_PATH}, using gs://spinnaker-artifacts/spin"
  SPIN_GCS_BUCKET_PATH=${PROD_SPIN_GCS_BUCKET_PATH}
fi

for elem in darwin,amd64 linux,amd64 windows,amd64; do
  IFS="," read -r os arch <<< "${elem}"
  echo "Building for $os $arch"
  env CGO_ENABLED=0 GOOS="$os" GOARCH="$arch" go build .

  file="spin"
  if [[ "$os" = "windows" ]]; then
    file="$file.exe"
  fi

  path=${SPIN_GCS_BUCKET_PATH}/${VERSION}/${os}/${arch}/
  echo "Copying $file to $path"

  gsutil cp "$file" "$path"
  rm "$file"
done

echo "$VERSION" > latest
gsutil cp latest ${SPIN_GCS_BUCKET_PATH}/
