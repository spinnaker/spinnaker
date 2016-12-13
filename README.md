# Halyard

[![Build Status](https://api.travis-ci.org/spinnaker/halyard.svg?branch=master)](https://travis-ci.org/spinnaker/halyard)

A tool to simplify configuring & running Spinnaker.

There are three parts to Halyard, the __halconfig__, the __daemon__, and
__hal__. In short, __hal__ is a Command Line Interface (CLI) that sends
commands to the __daemon__ to update the __halconfig__, which is ultimately
the source all configuration for your Spinnaker deployment. 

## halconfig

The __halconfig__ is a file is central to how Halyard configures your Spinnaker
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

If you want to examine only a particular account's configuration, run:

```
$ hal config provider PROVIDER get-account ACCOUNT
```

You can also enable/disable providers:

```
$ hal config provider PROVIDER enable
$ hal config provider PROVIDER disable
```

#### hal config generate

Once your config is ready to be deployed, you can automatically generate all of
Spinnaker's config with this command

```
$ hal config generate
```
