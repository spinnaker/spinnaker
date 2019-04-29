# Igor
[![Build Status](https://api.travis-ci.org/spinnaker/igor.svg?branch=master)](https://travis-ci.org/spinnaker/igor)

Igor provides a single point of integration with Jenkins, Travis and Git repositories ( BitBucket, Stash, and Github ) within Spinnaker.

Igor keeps track of the credentials for multiple Jenkins and/or Travis hosts and sends events to [echo](http://www.github.com/spinnaker/echo) whenever build information has changed.

## Configuring Jenkins Masters

In your configuration block ( either in igor.yml, igor-local.yml, spinnaker.yml or spinnaker-local.yml ), you can define multiple masters blocks by using the list format.

You can obtain a jenkins api token by navigating to `http://your.jenkins.server/me/configure`. ( where me is your username ).

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

Currently Jenkins is used within Spinnaker to trigger builds and provide artifact information for the bake stages.

## Configuring Travis Masters

In your configuration block ( either in igor.yml, igor-local.yml, spinnaker.yml or spinnaker-local.yml ), you can define multiple masters blocks by using the list format.

To authenticate with travis you use a "Personal access token" on a git user with permissions `read:org, repo, user`. This is added in `settings -> Personal access tokens`
on github/github-enterprise.

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

Currently Travis is used within Spinnaker to trigger pipelines and provide artifact information for the bake stages.

When parsing artifact information from Travis builds, Igor uses a default regex
that will match on output from the `art` CLI tool.  Different regexes than the
default may be configured using the `regexes` list.

## Git Repositories

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

Currently git credentials are used in Spinnaker pipelines to retrieve commit changes across different build versions.

## Docker Registries

Since [Clouddriver](https://github.com/spinnaker/clouddriver) already polls configured docker registries, Igor relies on Clouddriver to provide registry information. Therefore, first make sure you've [configured Clouddriver to poll your registries](http://www.spinnaker.io/v1.0/docs/target-deployment-configuration#section-docker-registry), and then add the following to your igor.yml or igor-local.yml file.

```
dockerRegistry:
  enabled: true
```

## Running Igor

Igor requires redis server to be up and running.

Start Igor via `./gradlew bootRun`. Or by following the instructions using the [Spinnaker installation scripts](https://www.github.com/spinnaker/spinnaker).

### Debugging

To start the JVM in debug mode, set the Java system property `DEBUG=true`:
```
./gradlew -DDEBUG=true
```

The JVM will then listen for a debugger to be attached on port 8188.  The JVM will _not_ wait for
the debugger to be attached before starting Igor; the relevant JVM arguments can be seen and
modified as needed in `build.gradle`.
