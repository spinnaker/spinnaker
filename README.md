Orca
====
[![Build Status](https://api.travis-ci.org/spinnaker/orca.svg?branch=master)](https://travis-ci.org/spinnaker/orca)

![Orca Logo](logo.jpg?raw=true)

Orca is the orchestration engine for Spinnaker.
It is responsible for taking an execution definition and managing the stages and tasks, coordinating the other Spinnaker services.

Orca _Executions_ (either Pipelines or Orchestrations) are composed of _Stages_ which in turn are composed of _Tasks_.
The tasks of a stage share a common context which can be queried across the entire execution allowing multiple stages to coordinate.
For example a _bake_ stage publishes details of the image it creates which is then used by a _deploy_ stage.

Orca is a stateless, horizontally scalable service.
Spinnaker's execution state is persisted to a backend store and work is distributed evenly through a work queue.
So long as your backend persistence layer has capacity, you can freely add or remove Orca servers to match workload demands.

## Internals Overview

### Domain

The primary domain model is an `Execution`, of which there are two types: `PIPELINE` and `ORCHESTRATION`.
The `PIPELINE` type is for pipelines while `ORCHESTRATION`s is unscheduled, ad-hoc API-submitted actions and what you see in the UI under the "Tasks" tab in an Application.

At this point in Spinnaker’s life, these two types are nearly programmatically identical, the key difference being that a Pipeline has a predefined persistent configuration, whereas an Orchestration is arbitarily configured at request-time and serial in execution.

For one Execution, there are one or many `Stage`s organized as a DAG (Directed Acyclic Graph).
An Execution supports concurrent branching logic of Stages.
These Stages define higher-order, user-friendly actions and concepts, such as "Resize Server Group" or "Deploy" and can be chained together to form complex delivery workflows.
Within a Stage, there are one or more `Task`s (not to be confused with the Tasks tab in the UI: Internally these are Orchestrations).
A Task can be considered an atomic unit of work, focused on a single action.
If you are familiar with the Spinnaker UI, you'll note that for every Stage, there are multiple line items that correlate to a Stage's runtime: These are often Tasks.
Tasks are always executed sequentially and serially.

It's important to note that a Stage is recursively composable.
One stage can have zero to many **Synthetic Stages**, that is, stages that can occur either `BEFORE` or `AFTER` its parent stage.
This is most easily illustrated in a Canary stage, which deploys multiple Server Groups (a baseline and canary).
While to the end-user this appears to be a single stage, it's actually a composition of multiple Deploy stages with some extra logic on top.
Lastly, an Execution's stage graph does not need to be known ahead of time.
An Execution can lay its tracks down ahead of itself as it is running, which is how some of the more advanced functionality is implemented, like automatic rollbacks or canary behaviors.

### Runtime

Orca uses a distributed queue library, [Keiko](http://github.com/spinnaker/keiko), to manage its work.
Keiko uses atomic messages to progress work through its lifecycle and allows Orca to first be resilient to node failure, as well as spread load evenly across the deployment making Orca easier to scale and operate.

Keiko itself is an abstraction around a delay queue, meaning it can deliver **Messages** immediately or at specific times in the future, as well as reschedule messages that have already been added to the queue for a new delivery time.
This delay queue functionality is pivotal to Orca's performance and operational characteristics when dealing with long running operations.

A Message is a fine-grained unit of work.
As an example, a `StartStage` message will verify that all upstream branches are complete and then perform any logic associated with preparing for the stage's execution.
It won't actually perform the stage's core actions.
A `RunTask` is really the only message that actually performs stage work that a user would be familiar with.

Some message types are re-deliverable and can be duplicated, whereas others are not.
For example, in a pipeline that has two concurrent stage branches that do not join at the end, there will be two `CompleteExecution` messages sent.
This is fine, because the `CompleteExecutionHandler` has logic to verify that indeed all stages are in a completed state before marking the entire execution as complete, whether it ultimately be a failure or success.

For every message type, there is a single correlated `Handler` that contains all of the logic for processing this `Message` type.
A Message's contents are small and the bare minimum of information to process the request: For instance, an Execution is referenced by ID rather than containing its entire state.
Orca builds atop these foundational Handlers in the form of `StageDefinitionBuilder` and other classes to build the DAG and implement actual Spinnaker orchestration logic.

The queue can be categorized into two parts: The `QueueProcessor`, and its Handlers.
Orca uses a single thread to run `QueueProcessor`, which polls the oldest messages "ready" off the queue.
A ready message is one whose delivery time is now or in the past: It is normal to have many more messages in the queue than those that are actually ready.

A separate worker thread pool is used for Handlers.
Orca depends on threads in that pool in order to scale sufficiently.
You can tune threads either by adjusting the pool size or by increasing the number of Orca instances.
By ignoring downstream bottlenecks Orca can scale horizontally to meet Spinnaker work demand.

If the queue starts to back up, Executions and their Tasks will begin to take longer to start or complete.
Sometimes the queue will back up because of downstream service pressure (for example, Clouddriver), but may also be due to Orca not having enough threads or persistence capacity.

One further note about the `QueueProcessor` and its thread pool.
The QueueProcessor polls the queue on a regular interval (default 10ms) and tries to fill its thread pool if there's work ready to process.
If the thread pool has a core size of 20, and 5 are already busy, it will attempt to take 15 messages off the queue immediately rather than one.
This is because in most cases a message takes less than 1ms to complete and we don't want Orca to idle unnecessarily.

## Storage & Work Queue

The following storage backends are supported for storing Execution state:

* Redis (default)
* MySQL (recommended)

The following backends are supported for the work queue:

* Redis

## Running Orca

Orca requires Redis (and optionally MySQL) to be up and running.

Start Orca via `./gradlew bootRun`, or by following the instructions using the [Spinnaker installation scripts](https://www.github.com/spinnaker/spinnaker).

### Debugging

To start the JVM in debug mode, set the Java system property `DEBUG=true`:

```
./gradlew -DDEBUG=true
```

The JVM will then listen for a debugger to be attached on port `8183`.
The JVM will _not_ wait for the debugger to be attached before starting Orca; the relevant JVM arguments can be seen and modified as needed in `build.gradle`.
