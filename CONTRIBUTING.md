# Contributing

Interested in contributing to Spinnaker? Please review the [contribution documentation](https://www.spinnaker.io/community/contributing/).

## Setup

### GOPATH (< 1.12)

Fetch the code

```bash
$ go get github.com/spinnaker/spin
```

**Note**: If you are using two-factor authentication with ssh keys to authenticate with GitHub,
you may need to run the following git command:

```bash
$ git config --global --add url."git@github.com:".insteadOf "https://github.com/"
```

for the `go get` command to work properly.

Enter the code's directory

```bash
$ cd $GOPATH/src/github.com/spinnaker/spin
```

Fetch dependencies and build with

```bash
$ go get -d -v -u
$ go build -v
```

from the root `spin/` directory.


### Go modules (>= 1.12)

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

Spin CLI uses [Swagger](https://swagger.io/) to generate the API client library for [Gate](https://github.com/spinnaker/gate). To update the client library:

- From the root of the Gate directory, execute `swagger/generate_swagger.sh` to create the `swagger.json` API spec.
- Get the [Swagger Codegen CLI](https://github.com/swagger-api/swagger-codegen). Use the version specified [here](https://github.com/spinnaker/spin/blob/master/gateapi/.swagger-codegen/VERSION).
- Remove the existing generated code from the spin directory `rm -r ~/spin/gateapi`
- Use the Swagger Codegen CLI to generate the new library and drop it into the spin project `java -jar ~/swagger-codegen-cli.jar generate -i ~/gate/swagger/swagger.json -l go -o ~/spin/gateapi`
- Commit the changes and open a PR.
