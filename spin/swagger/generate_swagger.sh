#!/usr/bin/env bash
# Use OpenAPI Generator for better OpenAPI 3.x support
OPENAPI_GENERATOR_VERSION='v7.10.0'

rm -rf ./gateapi
echo $PWD

## RUN THIS with the same user/group as the current user or the files it generates MAY not work properly
mkdir -p gateapi
chown $(id -u):$(id -g)  $PWD/gateapi

docker run --rm \
    -v "$PWD/../gate/swagger/:/tmp/gate" \
    -v "$PWD/gateapi/:/tmp/go/" \
    -u $(id -u):$(id -g) \
    "openapitools/openapi-generator-cli:${OPENAPI_GENERATOR_VERSION}" generate \
    -i /tmp/gate/swagger.json \
    -g go \
    -o /tmp/go/ \
    --skip-validate-spec \
    --package-name swagger

# Remove go.mod and go.sum since gateapi is part of the spin module
rm -f ./gateapi/go.mod ./gateapi/go.sum

# Detect sed in-place editing syntax (macOS vs Linux)
SED_INPLACE=(-i)
if sed --version 2>&1 | grep -q "GNU"; then
    # GNU sed (Linux)
    SED_INPLACE=(-i)
else
    # BSD sed (macOS)
    SED_INPLACE=(-i '')
fi

# Fix Go array literal syntax: OpenAPI Generator incorrectly generates ["value"] instead of []string{"value"}
# This regex finds patterns like: var name []type = ["value"] and converts to: var name []type = []type{"value"}
find ./gateapi -name "*.go" -type f -exec sed "${SED_INPLACE[@]}" 's/var \([^ ]*\) \[\]\([^ ]*\) = \["\([^"]*\)"\]/var \1 []\2 = []\2{"\3"}/g' {} \;

# Add missing context constants for authentication compatibility with existing spin client code
# Insert after the existing context variable definitions in configuration.go
sed "${SED_INPLACE[@]}" '/ContextOperationServerVariables = contextKey/a\
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
