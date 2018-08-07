# Spinnaker CLI

__This is under active development, and not yet intended for production use.__

Edit pipelines, applications & intents.

# Development

Fetch the code

```bash
go get github.com/spinnaker/spin
```

**Note**: If you are using two-factor authentication with ssh keys to authenticate with GitHub,
you may need to run the following git command:

```bash
git config --global --add url."git@github.com:".insteadOf "https://github.com/"
```

for the `go get` command to work properly.

Enter the code's directory

```bash
cd $GOPATH/src/github.com/spinnaker/spin
```

Fetch dependencies and build with with

```bash
go get -d -v
go build -v
```

from the root `spin/` directory.

Run using

```bash
./spin <cmds> <flags>
```

Test using

```bash
go test -v ./...
```

from the root `spin/` directory.

# Configuration

The configuration file for this CLI should live in `$HOME/.spin/config`. We include
a template with all the configuration options in the `config/` directory.

Here is also a small example of a configuration file:

```yaml
# ~/.spin/config

# Example using x509 authentication.
auth:
  enabled: true
  x509:
    keyPath: ~/.spin/keyfile
    certPath: ~/.spin/certfile
```

# Installation

Follow the instructions at [spinnaker.io](https://www.spinnaker.io/guides/spin/cli/#install-and-configure-spin-cli).

