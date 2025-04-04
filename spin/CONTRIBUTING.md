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

Spin CLI always builds the latest Gate API from the current branch whenever it builds itself.
