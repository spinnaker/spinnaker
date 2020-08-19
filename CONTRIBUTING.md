# Contributing

Interested in contributing to Spinnaker? Please review the [contribution documentation](https://www.spinnaker.io/community/contributing/).

## Setup

### Go

[Install Go 1.13.x](https://golang.org/doc/install).

### Go modules

Clone the repository to a directory outside of your GOPATH:

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

Test using

```bash
go test -v ./...
```

from the root `spin/` directory.

## Updating the Gate API

Spin CLI uses [Swagger](https://swagger.io/) to generate the API client library for [Gate](https://github.com/spinnaker/gate).

Spin CLI's `master` should be using Gate's `master` swagger definition. Similarly, each [spin release version](https://github.com/spinnaker/spin/tags) `version-{major}.{minor}.x` should match [Gate's tag](https://github.com/spinnaker/gate/tags) `version-{major}.{minor}`.

Example:
| Spin CLI version | Gate version   |
| ---------------- | ------------   |
| version-1.17.3   | version-1.17.0 |
| version-1.17.2   | version-1.17.0 |
| version-1.17.1   | version-1.17.0 |


To update the client library:

- Use the Swagger Codegen to generate the new library and drop it into the spin project
    ```bash
    GATE_REPO_PATH=PATH_TO_YOUR_GATE_REPO
    SWAGGER_CODEGEN_VERSION=$(cat gateapi/.swagger-codegen/VERSION)
    rm -rf gateapi/ \
    && docker run -it \
        -v "${GATE_REPO_PATH}/swagger/:/tmp/gate" \
        -v "$PWD/gateapi/:/tmp/go/" \
        "swaggerapi/swagger-codegen-cli:${SWAGGER_CODEGEN_VERSION}" generate -i /tmp/gate/swagger.json -l go -o /tmp/go/
    ```
- Commit the changes and open a PR.
