#!/usr/bin/env bash
SWAGGER_CODEGEN_VERSION='3.0.68'

rm -rf ./gateapi
echo $PWD
docker run \
    -v "$PWD/../gate/swagger/:/tmp/gate" \
    -v "$PWD/gateapi/:/tmp/go/" \
    "swaggerapi/swagger-codegen-cli-v3:${SWAGGER_CODEGEN_VERSION}" generate -i /tmp/gate/swagger.json -l go -o /tmp/go/
