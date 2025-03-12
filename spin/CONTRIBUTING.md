# Contributing

Interested in contributing to Spinnaker? Please review the [contribution documentation](https://spinnaker.io/docs/community/contributing/).

## Setup

### Go

[Install Go 1.17.x](https://golang.org/doc/install).

Clone the repository:

```bash
$ git clone https://github.com/spinnaker/spin
```

Afterward, use `go build` to build the program. This will automatically fetch dependencies.

```bash
$ go build
```

Upon first build, you may see output while the `go` tool fetches dependencies.

To verify dependencies match checksums under go.sum, run `go mod verify`.

To clean up any old, unused go.mod or go.sum lines, run `go mod tidy`.

## Running Spin

Run using

```bash
./spin <cmds> <flags>
```

## Running tests

From the root `spin/` directory run:

```bash
go test -v ./...
```

## Updating the Gate API

Spin CLI uses [Swagger](https://swagger.io/) to generate the API client library for [Gate](https://github.com/spinnaker/gate).

Spin CLI's `master` branch should be using Gate's `master` swagger definition.

Spin CLI's `release-{major}-{minor}.x` branch should be using Gate's
corresponding `release-{major}-{minor}.x` swagger definition.

To update the client library:

- Use the Swagger Codegen to generate the new library and drop it into the spin project

  ```bash
  # decide branch to update
  branch=release-1.##.x

  # check out appropriate Gate branch
  # assuming Gate checked out in same parent directory as spin and up to date
  cd ../gate
  git checkout "$branch"

  # generate Gate swagger client library branch
  swagger/generate_swagger.sh

  # check out appropriate Spin branch
  cd ../spin
  git checkout "$branch"

  # set Swagger Codegen tool version
  SWAGGER_CODEGEN_VERSION=$(cat gateapi/.swagger-codegen/VERSION)

  rm -rf gateapi/ \
  && docker run -it \
      -v "$PWD/../gate/swagger/:/tmp/gate" \
      -v "$PWD/gateapi/:/tmp/go/" \
      "swaggerapi/swagger-codegen-cli:${SWAGGER_CODEGEN_VERSION}" generate -i /tmp/gate/swagger.json -l go -o /tmp/go/

  # create branch off $branch and PR changes
  git checkout -b "$branch-swagger"
  ```

- Commit the changes and open a PR.
