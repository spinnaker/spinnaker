# Halyard

[![Build Status](https://api.travis-ci.org/spinnaker/halyard.svg?branch=master)](https://travis-ci.org/spinnaker/halyard)

A tool to simplify configuring & running Spinnaker.

There are three parts to Halyard, the __halconfig__, the __daemon__, and
__hal__. In short, __hal__ is a Command Line Interface (CLI) that sends
commands to the __daemon__ to update the __halconfig__, which is ultimately
the source all configuration for your Spinnaker deployment. 

## halconfig

The halconfig file is central to how Halyard configures your Spinnaker
deployment. Its goal is to centralize all configuration for your Spinnaker 
deployment (how to authenticate against your cloud providers, which CI system 
is in use, Spinnaker monitoring, etc...). It's consumed by the __daemon__ to 
generate config files for each Spinnaker subcomponent, after it's heavily 
validated.

For a detailed description, please read the [design doc](docs/design.md)

## daemon

The Halyard Daemon runs on a machine that has credentials for your cloud
provider, and will create/read a halconfig on that machine.

## hal

__hal__ is a CLI for making changes to your halconfig via the halyard daemon.

### hal config

To examine a particular cloud provider's configuration, run:

```
$ hal config provider PROVIDER 
```

Halyard will always attempt to validate unless you supply the `--no-validate`
flag:

```
$ hal config provider PROVIDER --no-validate
```

If you want to examine only a particular account's configuration, run:

```
$ hal config provider PROVIDER get-account ACCOUNT
```
