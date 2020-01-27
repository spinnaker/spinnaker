[![Build Status](https://api.travis-ci.org/spinnaker/igor.svg?branch=master)](https://travis-ci.org/spinnaker/igor)

[![](https://github.com/spinnaker/igor/workflows/Igor%20CI/badge.svg)](https://github.com/spinnaker/igor/actions?query=workflow%3A%22Igor+CI%22+branch%3Amaster)

Igor is a service that provides a single point of integration with Continuous Integration (CI) and Source Control Management (SCM) services for Spinnaker.

# Common Polling Architecture

Igor runs a number of pollers that all share the same common architecture. At a high level, they all:

- periodically get a list of items from an external resource (e.g. builds on a Jenkins master)
- compare that list against their own persisted cache of items (the difference is called _delta size_)
- send an echo event for each new item
- cache the new list of items

Features:

- *health*: igor has a `HealthIndicator` that reports `Down` if no pollers are running or if they have not had a successful polling cycle in a long time
- *locking*: pollers can optionally acquire a distributed lock in the storage system before attempting to complete a polling cycle. This makes it possible to run igor in a high-availability configuration and scale it horizontally.
- *safeguards*: abnormally large delta sizes can indicate a problem (e.g. lost or corrupt cache data) and cause downstream issues. If a polling cycle results in a delta size above the threshold, the new items will not be cached and events will not be submitted to echo to prevent a trigger storm. Manual action will be needed to resolve this, such as using the fast-forward admin endpoint: `/admin/pollers/fastforward/{monitorName}[?partition={partition}]`. Fast-forwarding means that all pending cache state will be polled and saved, but will not send echo notifications.

Relevant properties:

| *Property* | *Default value* | *Description* |
| --- | --- | --- |
| `spinnaker.build.pollInterval` | `60` | Interval in seconds between polling cycles |
| `spinnaker.pollingSafeguard.itemUpperThreshold` | `1000` | Defines the upper threshold for number of new items before a cache update cycle will be rejected | `locking.enabled` | `false` | Enables distributed locking so that igor can run on multiple nodes without interference |

Relevant metrics:

| *Metric* | *Type* | *Description* |
| --- | --- | --- |
| `pollingMonitor.newItems` | gauge | represents the number of new items cached by a given monitor during a polling cycle |
| `pollingMonitor.itemsOverThreshold` | gauge | 0 if deltaSize < threshold, deltaSize otherwise |
| `pollingMonitor.pollTiming` | timer | published for every polling cycle with the duration it took to complete |
| `pollingMonitor.failed` | counter | an error counter indicating a failed polling cycle |

All these metrics can be grouped by a `monitor` tag (e.g. `DockerMonitor`, `JenkinsMonitor`...) to track down issues.


# Storage

The following storage backends are supported:

- Redis
- Dynomite

Relevant properties:
```
redis:
  enabled: true
  connection: redis://host:port
```


# Integration with SCM services

The following SCM services are supported:

- Bitbucket
- Github
- Gitlab
- Stash

`Commit` controller classes expose APIs to retrieve lists of commits, such as `/github/{{projectKey}}/{{repositorySlug}}/compareCommits?from={{fromHash}}&to={{toHash}}`

At the moment, igor only exposes read APIs, there are no pollers and no triggers involving SCM services directly.

Relevant properties:

```
github:
  baseUrl: "https://api.github.com"
  accessToken: '<your github token>'
  commitDisplayLength: 8

stash:
  baseUrl: "<stash url>"
  username: '<stash username>'
  password: '<stash password>'

bitbucket:
  baseUrl: "https://api.bitbucket.org"
  username: '<bitbucket username>'
  password: '<bitbucket password>'
  commitDisplayLength: 7

gitlab:
  baseUrl: "https://gitlab.com"
  privateToken: '<your gitlab token>'
  commitDisplayLength: 8
```

# Integration with CI services

The following CI services are supported:

- Artifactory
- Nexus
- Concourse
- Gitlab CI
- Google Cloud Build (GCB)
- Jenkins
- Travis
- Wercker

For each of these services, a poller can be enabled (e.g. with `jenkins.enabled`) that will start monitoring new builds/pipelines/artifacts, caching them and submitting events to echo, thus supporting pipeline triggers. GCB is a bit different in that it doesn't poll and requires setting up [pubsub subscriptions](https://www.spinnaker.io/setup/ci/gcb/).

The `BuildController` class also exposes APIs for services that support them such as:

- getting build status
- listing builds/jobs on a master
- listing queued builds
- starting and stopping builds/jobs

These APIs are used to provide artifact information for bake stages.


## Configuring Jenkins Masters

In your configuration block (either in igor.yml, igor-local.yml, spinnaker.yml or spinnaker-local.yml), you can define multiple masters blocks by using the list format.

You can obtain a Jenkins API token by navigating to `http://your.jenkins.server/me/configure` (where `me` is your username).

```
jenkins:
  enabled: true
  masters:
    -
      address: "https://spinnaker.cloudbees.com/"
      name: cloudbees
      password: f5e182594586b86687319aa5780ebcc5
      username: spinnakeruser
    -
      address: "http://hostedjenkins.amazon.com"
      name: bluespar
      password: de4f277c81fb2b7033065509ddf31cd3
      username: spindoctor
```


## Configuring Travis Masters

In your configuration block (either in igor.yml, igor-local.yml, spinnaker.yml or spinnaker-local.yml), you can define multiple masters blocks by using the list format.

To authenticate with Travis you use a "Personal access token" on a git user with permissions `read:org, repo, user`. This is added in `settings -> Personal access tokens` on github/github-enterprise.

```
travis:
  enabled: true
  # Travis names are prefixed with travis- inside igor.
  masters:
  - name: ci # This will show as travis-ci inside spinnaker.
    baseUrl: https://travis-ci.org
    address: https://api.travis-ci.org
    githubToken: 6a7729bdba8c4f9abc58b175213d83f072d1d832
  regexes:
  - /Upload https?:\/\/.+\/(.+\.(deb|rpm))/
```

When parsing artifact information from Travis builds, igor uses a default regex
that will match on output from the `art` CLI tool.  Different regexes than the
default may be configured using the `regexes` list.


## Integration with Docker Registry

Clouddriver can be [configured to poll your registries](http://www.spinnaker.io/v1.0/docs/target-deployment-configuration#section-docker-registry). When that is the case, igor can then create a poller that will list the registries indexed by clouddriver, check each one for new images and submit events to echo (hence allowing Docker triggers)

Relevant properties:

- `dockerRegistry.enabled`
- requires `services.clouddriver.baseUrl` to be configured


# Running igor

Igor requires redis server to be up and running.

Start igor via `./gradlew bootRun`. Or by following the instructions using the [Spinnaker installation scripts](https://www.github.com/spinnaker/spinnaker).


## Debugging

To start the JVM in debug mode, set the Java system property `DEBUG=true`:
```
./gradlew -DDEBUG=true
```

The JVM will then listen for a debugger to be attached on port 8188.  The JVM will _not_ wait for the debugger to be attached before starting igor; the relevant JVM arguments can be seen and modified as needed in `build.gradle`.
