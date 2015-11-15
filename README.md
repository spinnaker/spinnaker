# Igor
Igor provides a single point of integration with Jenkins and Git repositories ( Stash and Github ) within Spinnaker.

Igor keeps track of the credentials for multiple Jenkins hosts and send events to [echo](http://www.github.com/echo) whenever build information has changed. 

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

This can be done locally via ./gradlew bootRun, which will start with an embedded cassandra instance. Or by following the instructions using the Spinnaker installation scripts.
