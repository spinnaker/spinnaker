# Keiko

Keiko is a simple, at-least-once queueing library originally built for
Spinnaker's Orca µservice.

## Concepts

Keiko consists of a number of components:

### The `Queue`

The queue itself accepts unique messages with an associated delivery time/
By default the delivery time is immediate but messages may be pushed for delivery at any point in the future.

### The `QueueProcessor`

Polls the queue for messages and hands them off to the appropriate `MessageHandler` according to the message type.
The `QueueProcessor` uses a single thread to poll the queue and invokes message handlers using a thread pool.

## Using

Include `com.netflix.spinnaker.keiko:keiko-redis:<version>` in your `build.gradle` file.

Implement message types and handlers extending `com.netflix.spinnaker.q.Message` and `com.netflix.spinnaker.q.MessageHandler` respectively.

### Using in a Spring Boot application

Include `com.netflix.spinnaker.keiko:keiko-redis-spring:<version>` in your `build.gradle` file.
It will pull `keiko-redis` in transitively so there is no need to declare dependencies on both modules.

## Implementing messages and handlers

For each message type you should implement a `Message` and a `MessageHandler`.
A `MessageHandler` can also handle a heirarchy of message types.

`Message` implementations should be immutable value types.
Kotlin data classes, Java classes annotated with Lombok's `@Value`, or just simple POJOs are ideal.

`MessageHandler` implementations may push other messages to the queue.
Messages should be processed quickly.
If processing time exceeds the `ackTimeout` value specified on the queue or an unhandled exception prevents the handler from returning normally, the message will get re-queued.

Handlers may re-push the same message they are processing in order to implement polling workflows.

## Configuring the queue in Spring Boot

## Telemetry

## Developing a new queue implementation
