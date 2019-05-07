# Spinnaker CLI

Edit pipelines, applications & intents.


# Installation & Configuration

Follow the instructions at [spinnaker.io](https://www.spinnaker.io/guides/spin/cli/#install-and-configure-spin-cli).


# Development

## GOPATH (< 1.12)

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

Fetch dependencies and build with with

```bash
$ go get -d -v -u
$ go build -v
```

from the root `spin/` directory.


## Go modules (>= 1.12)

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


## Running the program

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
