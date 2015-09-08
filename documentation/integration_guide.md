---
layout: toc-page
title: Integration Guide
id: integration_guide
lang: en
---

* Table of contents. This line is required to start the list.
{:toc}

<h1>Integration Guide</h1>

This guide provides information on how Spinnaker can be integrated with third party services. It documents both existing integrations ( stash, jenkins, etc ) as well as options for connecting to new systems. 

# Spinnaker API

# Spinnaker Events

# Jenkins Integration

Spinnaker integrates with Jenkins via the [Igor](https://www.github.com/spinnaker/igor) microservice.

Most commonly, jenkins builds are integrated into Spinnaker as either pipeline triggers or jenkins stages.

* *Pipeline triggers* launch a pipeline execution when a jenkins build has finished.
* *Jenkins stages*, in contrast, launch new jenkins job executions at specific points in the Spinnaker pipeline. 

## Configuration

Igor serves as an interface to multiple jenkins hosts. To configure the list of jenkins masters, you need to modify the igor.yml file that contains the configurations.

Below is an example of two jenkins hosts configured for use in Spinnaker:

~~~
jenkins:
  masters:
    -
      address: "http://spinhost.builds.example.net/"
      name: spinnakerhost
      password: f5e42494586b86687399aa5780eb2222
      username: foo
    -
      address: "http://spinhost2.anotherexample.net/"
      name: anotherhost
      password: a56761f6a117022008e3c44396937f24
      username: bar
~~~

In this example, we have two jenkins hosts. These will appear as *spinnakerhost* and *anotherhost* wherever the Spinnaker UI allows you to select a jenkins host. 

The username and password combination are used to identify the user accessing the jenkins host via its API. The user should have permissions to post new jobs if jenkins stages are being used in pipelines. 

Although Spinnaker allows you to specify the password you use to login to your Jenkins host, we recommend using an API token to access your build. The API token can be retrieved after login on your jenkins server through http://[my.jenkins.host]/user/[username]/configure. The value is under the heading "API Token".

## Jenkins Builds as pipeline triggers

### Understanding artifacts and the bake stage

### Passing information via property files

## Triggering Jenkins Jobs

