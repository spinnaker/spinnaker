```
 _           _                     _
| |__   __ _| |_   _  __ _ _ __ __| |
| '_ \ / _` | | | | |/ _` | '__/ _` |
| | | | (_| | | |_| | (_| | | | (_| |
|_| |_|\__,_|_|\__, |\__,_|_|  \__,_|
               |___/
```

Currently, it is not easy to setup and operate non-trivial installations of
[Spinnaker](https://github.com/spinnaker/spinnaker). It requires knowledge of
the behavior and operation of many independently configured services, and even 
with that knowledge, there is no existing solution for distributing or 
valdating configuration, updating Spinnaker's stateful services, or interacting 
with Spinnaker's API endpoint outside of the prebuilt UI. The goal of Halyard 
is to fix all of this. To understand what Halyard will become, and how it will
address these problems, we will separate the concerns of Halyard into three
pieces.

1. [Configuring Spinnaker](#configuring-spinnaker)

2. [Deploying and Updating Spinnaker](#deploying-and-updating-spinnaker)

3. [Operating the Spinnaker API](#operating-the-spinnaker-api)

## Configuring Spinnaker

The first stage in Halyards development will involve three parts.

1. [Versioning Spinnaker](#versioning-spinnaker)

2. [Distributing Authoritative
   Configuration](#distributing-authoritative-configuration)

3. [Generating User Configuration](#generating-user-configuration)

### Versioning Spinnaker

In order to have confidence in the Spinnaker installation being deployed, we
need to pin specific versions of Spinnaker microservices, as well as the
dependencies they require. We propose that the schema looks as follows:

```yaml
version: 1.x.x
services:                   # one entry for every service
  clouddriver:
    version: 1.x.x          # corresponds to travis-ci spinnaker release
    dependencies:           # list of name/version pairs
      - name: redis
        version: >2.0       # it is worth exploring version ranges here
  orca: ...
```

While the first iteration of Halyard development (configuring Spinnaker) will
not be able to deploy Spinnaker with the specified versions, it is important
that we pin sets of Spinnaker configuration to sets of Spinnaker service
versions by means of a holistic version number (HVN).

This version file should never need to exist on any machine deploying or
running Spinnaker, as it only needs to be readable by Halyard at
deployment/configuration time, meaning it should be hosted in a publicly 
readable web endpoint. However, the HVN itself will be present on whatever
machine is running Halyard to inform it of what configuration to read and what
to deploy.

### Distributing Authoritative Configuration

The idea is to have a single place that shared, authoritative Spinnaker config 
can be downloaded from. This will ultimately replace the configuration in
[spinnaker/config](https://github.com/spinnaker/spinnaker/tree/master/config)
by storing each `*.yaml` in a single versioned bucket. The bucket version will 
be mapped to Spinnaker HVNs to make it simple for Halyard to fetch the correct
configuration. The actual set of configuration will never need to be stored on
the maching running Halyard, only staged there during distribution of the
configuration.

### Generating User Configuration

This will be the most challenging part of Halyard's first phase of development.
In order to do this correctly, let's first list some goals:

1. The user should never have to open a text editor to write or edit Spinnaker
   configuration.

2. If the user does want to hand-edit configuration, Halyard should not
   interfere with that (but it will be an advanced use-case, and shall be
   treated as such).

3. Halyard should enable a user to configure multiple instances of Spinnaker
   all from the same machine.

To achieve these goals, Halyard will take a two-step approach to generating
Spinnaker configuration:

1. Receive a number of user commands (add an account, add a trigger, etc...)
   and store the resulting output in the `~/.hal/config` file.

2. Reading configuration from `~/.hal/config` and from the specified HVN, write
   out all Spinnaker configuration to `~/.spinnaker` (the default
   configuration directory).

Before exploring the semantics of the individual Halyard commands, let's look
at the `~/.hal` directory.

#### `~/.hal` Contents

## Deploying and Updating Spinnaker

## Operating the Spinnaker API
