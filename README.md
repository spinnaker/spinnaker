# Halyard

[![Build Status](https://api.travis-ci.org/spinnaker/halyard.svg?branch=master)](https://travis-ci.org/spinnaker/halyard)

A tool for configuring, installing, and updating Spinnaker.

## Installation

> WARNING: This tool is not yet stable.

```
$ wget https://raw.githubusercontent.com/spinnaker/halyard/master/install/stable/InstallHalyard.sh
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

Read the command reference [here](docs/commands.md).
