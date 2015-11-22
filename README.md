# Igor
[![Build Status](https://api.travis-ci.org/spinnaker/igor.svg?branch=master)](https://travis-ci.org/spinnaker/igor)

Igor provides a single point of integration with Jenkins and Git repositories ( Stash and Github ) within Spinnaker.

Igor keeps track of the credentials for multiple Jenkins hosts and sends events to [echo](http://www.github.com/spinnaker/echo) whenever build information has changed. 

## Configuring Jenkins Masters

In your configuration block ( either in igor.yml, igor-local.yml, spinnaker.yml or spinnaker-local.yml ), you can define multiple masters blocks by using the list format. 

You can obtain a jenkins api token by navigating to `http://your.jenkins.server/me/configure`. ( where me is your username ).

```
jenkins:
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

## Git Repositories

```
github:
  baseUrl: "https://api.github.com"
  accessToken: '<your token>'
  commitDisplayLength: 8

stash:
  baseUrl: "<stash url>"
  username: '<stash username>'
  password: '<stash password>'
```
Currently git credentials are used in Spinnaker pipelines to retrieve commit changes across different build versions. 

## Running Igor

Igor requires redis server to be up and running.

Start Igor via `./gradlew bootRun`. Or by following the instructions using the [Spinnaker installation scripts](https://www.github.com/spinnaker/spinnaker).
