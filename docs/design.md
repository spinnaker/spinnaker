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
the behavior and operation of many independently configured services - and even
with that knowledge - there is no existing solution for distributing or
valdating configuration, updating Spinnaker's stateful services, or interacting
with Spinnaker's API outside of the prebuilt UI. The goal of Halyard
is to provide a solution for all of this. To understand what Halyard will 
become, and how it will address these problems, we will separate the 
concerns of Halyard into three stages.

- 1) [Configuring Spinnaker](#1-configuring-spinnaker)

- 2) [Deploying and Updating Spinnaker](#2-deploying-and-updating-spinnaker)

- 3) [Operating the Spinnaker API](#3-operating-the-spinnaker-api)

## 1) Configuring Spinnaker

The first stage in Halyards development will involve three parts.

- 1.1) [Versioning Spinnaker](#1-1-versioning-spinnaker)

- 1.2) [Distributing Authoritative
   Configuration](#1-2-distributing-authoritative-configuration)

- 1.3) [Generating User Configuration](#1-3-generating-user-configuration)

### 1.1) Versioning Spinnaker

In order to have confidence in the Spinnaker installation being deployed, we
need to pin specific versions of Spinnaker microservices, as well as the
dependencies they require. We propose that the schema looks as follows:

```yaml
version: 1.4.0
services:                   # one entry for every service
  clouddriver:
    version: 1.320.0        # corresponds to travis-ci spinnaker release
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
deployment/configuration time, meaning it could be hosted at a publicly
readable web endpoint. However, the HVN itself will be present on whatever
machine is running Halyard to inform it of what configuration to read and what
to deploy.

## 1.2) Distributing Authoritative Configuration

The idea is to have a single place that shared, authoritative Spinnaker
configuration can be downloaded from. This will ultimately replace the
configuration in
[spinnaker/config](https://github.com/spinnaker/spinnaker/tree/master/config)
by storing each `*.yaml` file in a single versioned bucket. The bucket version
will be mapped to Spinnaker HVNs to make it simple for Halyard to fetch the
correct configuration. The actual set of configuration will never need to be
stored on the maching running Halyard, only staged there during distribution
of the configuration.

### 1.3) Generating User Configuration

This will be the most challenging part of Halyard's first phase of development.
In order to do this correctly, let's first list some goals:

- a) The user should never have to open a text editor to write or edit
  Spinnaker confiuration

- b) If the user does want to hand-edit configuration, Halyard should not
  interfere with that (but it will be an advanced use-case, and shall be
  treated as such).

- c) Halyard should enable a user to configure multiple instances of Spinnaker
  all from the same machine.

To achieve these goals, Halyard will take a two-step approach to generating
Spinnaker configuration:

- a) Receive a number of user commands (add an account, add a trigger, etc...)
  and store the resulting output in the `~/.hal/config` file.

- b) Reading configuration from `~/.hal/config` and from the specified HVN, write
  out all Spinnaker configuration to `~/.spinnaker` (the default
  configuration directory).

Before exploring the semantics of the individual Halyard commands, let's look
at the `~/.hal` directory.

#### `~/.hal` Contents

The directory structure will look something like this:

```
.hal/
  config                    # all halyard spinnaker entries
  my-spinnaker-1/           # optional directory with per-install overrides
    clouddriver-local.yml   # optional -local.yml files with config changes
  my-spinnaker-2/
    igor-local.yml
    echo-local.yml
```

The takeaway for the above diagram is that only `~/.hal/config` is required,
and that for each installation of Spinnaker installed you can optionally
provide your own `*-local.yml` files.

The contents of `~/.hal/config` will look like this:

```yaml
current-deployment: my-spinnaker-1     # which deployment to operate on
deployment-configuration:
  - name: my-spinnaker-1
    accounts:
      kubernetes:                      # provider-specific details
        - name: my-kubernetes-account
          context: ...
        - name: my-other-kubernetes-account
      google:
        - name: ...
    webhooks:                          # not the best name, TODO
      jenkins:                         # CI-specific details
        - name: ci-server
          endpoint: ...
  - name: my-spinnaker-2
    accounts: ...
```

#### `~/.hal` Semantics

Now that we know what will be stored in the `~/.hal` directory, we need to
explain how to print

## 2. Deploying and Updating Spinnaker

## 3. Operating the Spinnaker API
