#!/usr/bin/env bash

helm package manifests/demo

# todo(lwander): incorporate versioning
gsutil cp demo-0.1.0.tgz gs://spinnaker-playground/manifests/demo/demo.tgz

rm demo-0.1.0.tgz

gsutil cp manifests/production/values.yaml gs://spinnaker-playground/manifests/demo/production/values.yaml

gsutil cp manifests/staging/values.yaml gs://spinnaker-playground/manifests/demo/staging/values.yaml
