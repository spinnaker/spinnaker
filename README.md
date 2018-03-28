Orca
====
[![Build Status](https://api.travis-ci.org/spinnaker/orca.svg?branch=master)](https://travis-ci.org/spinnaker/orca)

![Orca Logo](logo.jpg?raw=true)

Orca is the orchestration engine for Spinnaker.
It is responsible for taking a pipeline or task definition and managing the stages and tasks, coordinating the other Spinnaker services.

Orca pipelines are composed of _stages_ which in turn are composed of _tasks_.
The tasks of a stage share a common context and can publish to a global context shared across the entire pipeline allowing multiple stages to co-ordinate.
For example a _bake_ stage publishes details of the image it creates which is then used by a _deploy_ stage.

Orca persists a running execution to Redis.

### Debugging

To start the JVM in debug mode, set the Java system property `DEBUG=true`:
```
./gradlew -DDEBUG=true
```

The JVM will then listen for a debugger to be attached on port 8183.  The JVM will _not_ wait for
the debugger to be attached before starting Orca; the relevant JVM arguments can be seen and
modified as needed in `build.gradle`.
