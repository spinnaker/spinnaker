set -e
set -x

if [ ! -d ${HOME}/google-cloud-sdk ]; then
  wget https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/google-cloud-sdk-206.0.0-linux-x86_64.tar.gz -O install.tar.gz
  tar -xvzf install.tar.gz

  ./google-cloud-sdk/install.sh -q
fi

export PATH=$PATH:`pwd`/google-cloud-sdk/bin

gcloud auth activate-service-account --key-file key.json
gcloud components install gsutil -q

if [ -z "$VERSION" ]; then
  echo -e "No version to release specified with the \$VERSION env var, exiting"
  exit 1
fi

if [ -z "$GCS_BUCKET_PATH" ]; then
  echo "No GCS bucket specified using \$GCS_BUCKET_PATH, using gs://spinnaker-artifacts/spin"
  GCS_BUCKET_PATH="gs://spinnaker-artifacts/spin"
fi

for elem in darwin,amd64 linux,amd64; do
  IFS="," read os arch <<< "${elem}"
  echo "Building for $os $arch"
  env CGO_ENABLED=0 GOOS=$os GOARCH=$arch go build .

  path=${GCS_BUCKET_PATH}/${VERSION}/${os}/${arch}/
  echo "Copying to $path"

  gsutil cp spin $path
  rm spin
done
