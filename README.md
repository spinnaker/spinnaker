# Echo
[![Build Status](https://api.travis-ci.org/spinnaker/echo.svg?branch=master)](https://travis-ci.org/spinnaker/echo)
Echo serves as a router for events that happen within Spinnaker.

## Outgoing Events

It provides integrations for outgoing notifications in the echo-notifications package via:

*  email
*  Slack
*  Hipchat
*  sms ( via Twilio )

Echo is also able to send events within Spinnaker to a predefined url, which is configurable under the echo-rest module.

You can extend the way in which Echo events are sent by implementing the `EchoEventListener` interface.


## Event Types

Currently, echo receives build events from igor and orchestration events from orca.

## Incoming Events
Echo also integrates with [igor](http://www.github.com/spinnaker/igor), [front50](http://www.github.com/spinnaker/front50) and [orca](http://www.github.com/spinnaker/orca) to trigger pipeline executions.

It does so via two modules:

* pipeline-triggers :  Responsible firing off events from Jenkins Triggers
* scheduler : Triggers pipelines off cron expressions. Support for cron expressions is provided by Netflix's [Fenzo](https://github.com/netflix/fenzo) library.

## Running Echo
This can be done locally via `./gradlew bootRun`, which will start with an embedded cassandra instance. Or by following the instructions using the [Spinnaker installation scripts](http://www.github.com/spinnaker/spinnaker).

### Debugging

To start the JVM in debug mode, set the Java system property `DEBUG=true`:
```
./gradlew -DDEBUG=true
```

The JVM will then listen for a debugger to be attached on port 8189.  The JVM will _not_ wait for
the debugger to be attached before starting Echo; the relevant JVM arguments can be seen and
modified as needed in `build.gradle`.

[//]: # "Only here to retrigger the echo build"
