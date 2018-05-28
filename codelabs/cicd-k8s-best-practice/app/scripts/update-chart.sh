#!/usr/bin/env bash

helm package manifests/demo

# todo(lwander): incorporate versioning
gsutil cp demo-0.1.0.tgz gs://spinnaker-playground/manifests/demo/demo.tgz

rm demo-0.1.0.tgz
