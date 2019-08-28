# Echo
[![Build Status](https://api.travis-ci.org/spinnaker/echo.svg?branch=master)](https://travis-ci.org/spinnaker/echo)

`Echo` serves as two purposes within Spinnaker:  
1. a router for events (e.g. a new build is detected by Igor which should trigger a pipeline)
2. a scheduler for CRON triggered pipelines.

The following high level diagram shows how events flow through `echo`:  
![echo high level architecture](docs/echo.png)
  

1. `igor` sends events to `echo` when it discovers a delta in a service that it monitors (see [igor readme](https://github.com/spinnaker/igor/#common-polling-architecture) for more details)  
    e.g. A new build has completed or a new docker image was found in the docker registry

2. `gate` sends events to `echo` as a result of user triggered actions  
    e.g. User manually kicks off a pipeline from the UI (`deck`) or a user submit a pipeline or an orchestration for execution via the API (`gate`)

3. `swabbie` sent a notification request to `echo`
    e.g. A resource is about to be deleted and swabbie would like an email notification to be sent out to the owner
   
4. `echo` submits the pipeline/orchestration to `orca` for execution

5. `orca` sends events to `echo` when:
    - a stage is starting/completed so `echo` can send notifications if any are defined on the stage
    - a pipeline (or orchestration) is starting/completed so `echo` can send notification as above
    - a manual judgement stage is reached - a notifcation from 
    - the user has clicked the page button on the application page

6. `echo` uses external services (e.g. email/slack) to send notifications.  
    Notifications can either be a result of an event received by `echo` (e.g. stage completed which has a notification on completion), or a specific notification request from another service (e.g. orca will send a notifcation for Manual Judgement stage)

7. `echo` can also send events to any URL (ala webhook style)


## Outgoing Events

Echo provides integrations for outgoing notifications in the `echo-notifications` package via:

* email
* Slack
* Bearychat
* Google Chat
* sms (via Twilio)
* PagerDuty

`Echo` is also able to send events within Spinnaker to a predefined url, which is configurable under the `echo-rest` module.

You can extend the way in which `echo` events are sent by implementing the `EchoEventListener` interface.


## Event Types
Currently, `echo` receives build events from [igor](http://www.github.com/spinnaker/igor) and orchestration events from [orca](http://www.github.com/spinnaker/orca).

## Incoming Events
Echo also integrates with [igor](http://www.github.com/spinnaker/igor), [front50](http://www.github.com/spinnaker/front50) and [orca](http://www.github.com/spinnaker/orca) to trigger pipeline executions.

It does so via two modules:

* `pipeline-triggers`:  Responsible firing off events from Jenkins Triggers
* `scheduler`: Triggers pipelines off cron expressions. Support for cron expressions is provided by [quartz](http://www.quartz-scheduler.org)

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

## Configuration
`echo` can run in two modes: **in-memory** and **SQL**.

**In-memory** mode keeps all CRON trigger information in RAM.  
While this is simpler to configure (this is the default) the in-memory mode does not provide for any redundancy because it requires that a single instance of `echo` scheduler be running. If there are multiple instances, they will all attempt to start executions for a given CRON trigger. There is no locking, leader election, or any other kind of coordination between scheduler instances using the in-memory mode.  
If/when this single instance goes down, CRON triggers will not fire.

**SQL** mode keeps all CRON trigger information in a single SQL database. This allows for multiple `echo` scheduler instances to run providing redundancy (only one instance will trigger a given CRON).

To run in SQL mode you will need to initialize the database and provide a connection string in `echo.yml` (note these instructions assume MySQL).
1. Create a database.
2. Initialize the database by running the script (MySQL dialect provided [here](echo-scheduler/src/main/resources/db/database-mysql.sql))
3. Configure the SQL mode in `echo.yml` (obviously, change the connection strings below):
    ```yaml
    sql:
      enabled: true
      connectionPool:
        jdbcUrl: jdbc:mysql://localhost:3306/echo?serverTimezone=UTC
        user: echo_service
      migration:
        jdbcUrl: jdbc:mysql://localhost:3306/echo?serverTimezone=UTC
        user: echo_migrate
    ```

See [Sample deployment topology](#sample-deployment-topology) for additional information

### Configuration options
`echo` has several configuration options (can be specified in `echo.yml`), key ones are listed below:  
* `scheduler.enabled` (default: `false`)  
    when set to `true` this instance will schedule and trigger CRON events
* `scheduler.pipelineConfigsPoller.enabled` (default: `false`)  
    when `true`, will synchronize pipeline triggers (set this to `true` if you enable `scheduler` unless running a missed scheduler configuration)
* `scheduler.compensationJob.enabled` (default: false)  
    when `true` this instance will poll for missed CRON triggers and attempt to re-trigger them (see [Missed CRON scheduler](#Missed-CRON-scheduler))
* `orca.pipelineInitiatorRetryCount` (default: `5`)  
    Number of retries on `orca` failures (leave at default)
* `orca.pipelineInitiatorRetryDelayMillis` (default: 5000ms)  
    Number of milliseconds between retries to `orca` (leave at default)

## Missed CRON scheduler
The missed CRON scheduler is a feature in `echo` that ensures that CRON triggers are firing reliably. It is enabled by setting `scheduler.compensationJob.enabled` configuration option.  
In an event that a CRON trigger fails to fire or it fires but, for whatever reason, the execution doesn't start the missed CRON scheduler will detect it and attempt to re-trigger the pipeline.  
The main scenario when missed cron scheduler is useful is for main scheduler outages either planned (upgrade) or unplanned (hardware failure).    
Missed scheduler should be run as a separate instance as that will provide the most benefit and the resilience needed. Most situation likely don't necessitate the need for a missed scheduler instance, especially if you elect to run in SQL mode. (With the SQL mode support and pending additional investigation this feature will likely be removed all-together)

## Sample deployment topology
Here are two examples of what configurations you can deploy `echo`.

|                   | Using in-memory            | Using SQL |
|-------------------|----------------------------|-----------|
|**Server Group 1** |3x `echo`                   | 3x `echo` with `echo-scheduler`
|**Server Group 2** |1x `echo-scheduler`         | 1x `echo-missed-scheduler`*
|**Server Group 3** |1x `echo-missed-scheduler`* | n/a

\* _optional `echo-missed-scheduler` see [Missed CRON scheduler](#Missed-CRON-scheduler)_

If you opt for using an in-memory execution mode, take care when deploying upgrades to `echo`.
Since only instance should be running at a time, a rolling-push strategy will need to be used. Furthermore, if using `echo-missed-scheduler`, make sure to upgrade `echo-scheduler` followed by `echo-missed-scheduler` to ensure pipelines (which had a trigger during the deploy period) are re-triggered correctly after deploy.

The following are configuration options for each server group (note that other configurations options will be required, which halyard will configure):  
`echo` (this instance handles general events)   
```yaml
scheduler:
  enabled: false
  pipelineConfigsPoller:
    enabled: false
  compensationJob:
    enabled: false
```

`echo-scheduler` (this instance triggers pipelines on a CRON)  
```yaml
scheduler:
  enabled: true
  pipelineConfigsPoller:
    enabled: true
  compensationJob:
    enabled: false
```

`echo-missed-scheduler` (this instance triggers "missed" pipelines)  
```yaml
scheduler:
  enabled: true
  pipelineConfigsPoller:
    enabled: false
  compensationJob:
    enabled: true
    pipelineFetchSize: 50

    # run every 1 min to minimize the skew between expected and actual trigger times
    recurringPollIntervalMs: 60000

    # look for missed cron triggers in the last 5 mins (allows for a restart of the service)
    windowMs: 300000
```

## Monitoring
`echo` emits numerous metrics that allow for monitoring its operation.  
Some of the key metrics are listed below:

* `orca.trigger.success`  
   Number of successful triggers. That is when `orca` returns `HTTP 200` on a given trigger  

* `orca.trigger.errors`  
   Number of failed triggers. When `orca` fails to execute a pipeline (returns non-successful HTTP status code).  
   This is a good metric to monitor as it indicates either invalid pipelines or some system failure in triggering pipelines

* `orca.trigger.retries`  
   Number of retries to `orca`. Failed calls to `orca` will be retried (assuming they are network type errors).  
   Consistent non-zero numbers here means there is likely a networking issue communicating with `orca` and should be investigated.

* `echo.triggers.sync.error`,  
   `echo.triggers.sync.failedUpdateCount`, and  
   `echo.triggers.sync.removeFailCount`  
    Indicates a failure during trigger synchronization. This likely means there are pipeline with invalid CRON expressions which will not trigger.  
    `echo` logs should provide additional information as to the cause of the issue
