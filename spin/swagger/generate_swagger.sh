#!/usr/bin/env bash
# Use OpenAPI Generator for better OpenAPI 3.x support
OPENAPI_GENERATOR_VERSION='v7.10.0'

rm -rf ./gateapi
echo $PWD
docker run --rm \
    -v "$PWD/../gate/swagger/:/tmp/gate" \
    -v "$PWD/gateapi/:/tmp/go/" \
    "openapitools/openapi-generator-cli:${OPENAPI_GENERATOR_VERSION}" generate \
    -i /tmp/gate/swagger.json \
    -g go \
    -o /tmp/go/ \
    --skip-validate-spec \
    --package-name swagger

# Remove go.mod and go.sum since gateapi is part of the spin module
rm -f ./gateapi/go.mod ./gateapi/go.sum

# Fix Go array literal syntax: OpenAPI Generator incorrectly generates ["value"] instead of []string{"value"}
# This regex finds patterns like: var name []type = ["value"] and converts to: var name []type = []type{"value"}
find ./gateapi -name "*.go" -type f -exec sed -i '' 's/var \([^ ]*\) \[\]\([^ ]*\) = \["\([^"]*\)"\]/var \1 []\2 = []\2{"\3"}/g' {} \;

# Add missing context constants for authentication compatibility with existing spin client code
# Insert after the existing context variable definitions in configuration.go
sed -i '' '/ContextOperationServerVariables = contextKey/a\
\
	// ContextAccessToken takes a string oauth2 access token as authentication for the request.\
	ContextAccessToken = contextKey("accesstoken")\
\
	// ContextBasicAuth takes BasicAuth as authentication for the request.\
	ContextBasicAuth = contextKey("basic")\
\
	// ContextOAuth2 takes an oauth2.TokenSource as authentication for the request.\
	ContextOAuth2 = contextKey("token")
' ./gateapi/configuration.go
