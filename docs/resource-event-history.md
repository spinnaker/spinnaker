## Resource event history

For each managed resource, keel keeps a history of *resource events*.
Users can view this history in the clusters view of Deck by hovering the mouse of over the Managed icon and clicking the "History button".

### How events get recorded

The [ResourceHistoryListener] listens for Spring events subclassed from [ResourceEvent], and writes the events to the databaase.
For example, the [ResourceActuator] publishes a [ResourceActuationLaunched] event after the actuator starts the process of creating or updating a resource.

### Serving events from the API

The [EventController] serves a list of resource events at: `/resources/events/{id}`.

Here are two example events, which corresponds to a resource with id `ec2:cluster:prod:fnord-prod`, which would be accessible at `/resources/events/ec2%3Acluster%3Aprod%3Afnord-prod`

```json
[
  {
    "type": "ResourceActuationLaunched",
    "kind": "ec2/cluster@v1.1",
    "id": "ec2:cluster:prod:fnord-prod",
    "version": 1,
    "application": "fnord",
    "plugin": "ClusterHandler",
    "tasks": [
      {
        "id": "01F0C4M1CT06WTEAF5NPKJSHRH",
        "name": "Deploy fnord-0.950.0-h1278.3cd2ac9 to server group fnord-prod in prod/us-east-1"
      }
    ],
    "timestamp": "2021-03-09T18:40:07.594674Z",
    "displayName": "Updating resource to match current definition",
    "level": "INFO",
    "scope": "resource",
    "ref": "ec2:cluster:prod:fnord-prod"
  },
  {
    "type": "ResourceCheckError",
    "kind": "ec2/cluster@v1.1",
    "id": "ec2:cluster:prod:fnord-prod",
    "version": 1,
    "application": "fnord",
    "timestamp": "2021-03-16T23:38:36.746391Z",
    "exceptionType": "com.netflix.spinnaker.keel.plugin.CannotResolveCurrentState",
    "exceptionMessage": "Unable to resolve current state of ec2:cluster:prod:fnord-prod due to: HTTP 403 ",
    "displayName": "Failed to check resource status",
    "level": "ERROR",
    "scope": "resource",
    "message": "Unable to resolve current state of ec2:cluster:prod:fnord-prod due to: HTTP 403 ",
    "origin": "system",
    "ref": "ec2:cluster:prod:fnord-prod"
  }
]
```

The UI will show the `displayName` as the summary.
If the `message` field is present, the UI will show the message as additional detail.
If the `tasks` field is present, the UI will show the task name with link to the task.


[ResourceHistoryListener]: ../keel-core/src/main/kotlin/com/netflix/spinnaker/keel/events/ResourceHistoryListener.kt
[ResourceEvent]: ../keel-core/src/main/kotlin/com/netflix/spinnaker/keel/events/ResourceEvent.kt
[ResourceActuator]: ../keel-core/src/main/kotlin/com/netflix/spinnaker/keel/actuation/ResourceActuator.kt
[ResourceActuationLaunched]: ../keel-core/src/main/kotlin/com/netflix/spinnaker/keel/events/ResourceEvent.kt
[EventController]: ../keel-web/src/main/kotlin/com/netflix/spinnaker/keel/rest/EventController.kt
