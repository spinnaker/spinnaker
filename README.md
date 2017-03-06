# Halyard

[![Build Status](https://api.travis-ci.org/spinnaker/halyard.svg?branch=master)](https://travis-ci.org/spinnaker/halyard)

A tool for configuring, installing, and updating Spinnaker.

## Installation

> WARNING: This tool is not yet stable.

```
$ wget https://raw.githubusercontent.com/spinnaker/halyard/master/InstallHalyard.sh
$ sudo bash InstallHalyard.sh
```

# Overview

There are three parts to Halyard, the __halconfig__, the __daemon__, and
__hal__. In short, __hal__ is a Command Line Interface (CLI) that sends
commands to the __daemon__ to update the __halconfig__, which is ultimately
the source of all configuration for your Spinnaker deployment. 

## halconfig

The __halconfig__ is a file that is central to how Halyard configures your Spinnaker
deployment. Its goal is to centralize all configuration for your Spinnaker 
deployment (how to authenticate against your cloud providers, which CI system 
is in use, Spinnaker monitoring, etc...). 

For a detailed description, please read the [design doc](docs/design.md)

## daemon

The __daemon__ validates and generates Spinnaker config using your
__halconfig__. It must run on a machine that has any credentials needed by
Spinnaker in order to validate your configuration.

## hal

__hal__ is a CLI for making changes to your __halconfig__ via the __daemon__.

### hal config

The `hal config` set of commands are used to operate on your __halconfig__ file.

#### hal config provider

To examine a particular cloud provider's configuration, run:

```
$ hal config provider PROVIDER
```

Halyard will always attempt to validate unless you supply the `--no-validate`
flag:

```
$ hal config provider PROVIDER --no-validate
```

You can also enable/disable providers:

```
$ hal config provider PROVIDER enable
$ hal config provider PROVIDER disable
```

If you want to examine only a particular account's configuration, run:

```
$ hal config provider PROVIDER account get ACCOUNT-NAME
```

To add an account, run:

```
$ hal config provider PROVIDER account add ACCOUNT-NAME [provider-specific flags]
```

To edit an account, run:

```
$ hal config provider PROVIDER account edit ACCOUNT-NAME [provider-specific flags]
```

To delete an account, run:

```
$ hal config provider PROVIDER account delete ACCOUNT-NAME
```

If you want to see your provider's bakery configuration, run:

```
$ hal config provider PROVIDER bakery
```

If you want to edit your provider's bakery default configuration, run:

```
$ hal config provider PROVIDER bakery edit [provider-specific flags]
```

If you want to examine only a particular images's configuration, run:

```
$ hal config provider PROVIDER bakery base-image get IMAGE-ID
```

To add an image, run:

```
$ hal config provider PROVIDER bakery base-image add IMAGE-ID [provider-specific flags]
```

To edit an image, run:

```
$ hal config provider PROVIDER bakery base-image edit IMAGE-ID [provider-specific flags]
```

To delete an image, run:

```
$ hal config provider PROVIDER bakery base-image delete IMAGE-ID
```

#### hal config deploy

To show how your deployment of Spinnaker has been configured, run:

```
$ hal config deploy
```

The default is to download & run packages locally. To change this, run:

```
$ hal config deploy edit [--flags]
```

If you're unsure of what needs to be set, run:

```
$ hal config deploy edit --help
```

#### hal config features

To display the state of the feature flags, run:

```
$ hal config features
```

To enable either the experimental or non-standard features of Spinnaker, use
the command:

```
$ hal config features edit [--feature true/false]
```

If you're unsure of what features are available, check:

```
$ hal config features edit --help
```

#### hal config security

To show how your security has been configured, run:

```
$ hal config security
```

By default, Spinnaker runs without SSL, authentication, or authorization
mechanisms in place. This can be changed with the following set of 
commands.

_OAuth2.0_

To see how OAuth2 was configured, run:

```
$ hal config security oauth2
```

To see enable/disable OAuth2 configuration, run:

```
$ hal config security oauth2 [enable/disable]
```

To configure OAuth2 authentication, run:

```
$ hal config security oauth2 --provider <provider> [provider flags]
```

If you're unsure of what needs to be set, run:

```
$ hal config security oauth2 --help
```

#### hal config storage

To show how your storage has been configured, run:

```
$ hal config storage
```

Spinnaker needs persistant storage configured in the form of an S3 or GCS
bucket. To configure this, run:

```
$ hal config storage edit [--flags]
```

If you're unsure of what needs to be set, run:

```
$ hal config storage edit --help
```

#### hal config generate

Once your config is ready to be deployed, you can automatically generate all of
Spinnaker's config with this command

```
$ hal config generate
```

Since Halyard isn't at a stable release, all config will be written to
`~/.halyard`, rather than `~/.spinnaker`. You can change this behavior by
configuring the Spring parameter `spinnaker.config.output.directory`.

If you have profiles/config that halyard doesn't handle yet, you can put them
in `~/.hal/$DEPLOYMENT/` and they will be placed into the output directory for
you. Unless you have made custom changes to your halconfig,
`$DEPLOYMENT == default`, so a typical configuration might look like:

```
$ tree ~/.hal
/home/lwander/.hal
├── config
└── default
    ├── clouddriver-local.yml
    ├── echo.yml
    └── gate-googleOAuth.yml

1 directory, 4 files
```

### hal deploy

Deploy your configured Spinnaker installation. Prior to running this, it's
recommended that you've run `hal config deploy [...]` to ensure your deployment
has the correct footprint.

#### hal deploy plan

Examine what your deployed Spinnaker will look like with this command:

```
$ hal deploy plan
```

#### hal deploy run

Deploy Spinnaker:

```
$ hal deploy run
```

### hal versions

List the available versions of Spinnaker to deploy.

```
$ hal versions
```

## Getting Started

Since Halyard is not yet being distributed, to use it you will have to build it
yourself.

### Running the Daemon

Run (and leave running) the following command:

```
$ ./gradlew         # this will take a long time the first time it's run
```

### Running the CLI

```
$ cd halyard-cli
$ make              # if this command hangs, restart the daemon
$ ./hal
```

The `./hal` script must be run from inside the `halyard-cli` folder.

There is no command-completion yet, but at any point the `--help` flag should
point you in the right direction.

### Sample usage

Assuming you're running the Daemon & have compiled the CLI, you can try the
following:

```
$ ./hal config provider docker-registry enable
$ ./hal config provider docker-registry account add my-dockerhub-account \
    --address index.docker.io \
    --repositories library/nginx
$ ./hal config provider kubernetes enable
$ ./hal config provider kubernetes account add my-kubernetes-account \
    --docker-registries my-dockerhub-account
$ cat ~/.hal/config
$ ./hal config generate
$ cat ~/.halyard/*
```
