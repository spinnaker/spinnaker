# keel

Spinnaker's declarative service.

## test payload

`PUT https://localhost:8087/intents`

```json
{
	"intents": [
		{
			"kind": "Parrot",
			"schema": "1",
			"spec": {
				"application": "keel",
				"description": "hello"
			},
			"status": "ACTIVE"
		}
	],
	"dryRun": true
}
```

## Design

**NOTICE: This is an early draft of the system design**

Keel is designed around the idea of state [Intents][1], which is a statically defined
definition of desired system state. When an Intent is provided into Keel, it will store
it away in Front50, then continually converge and re-converge on the desired state that
is defined by the Intent. If an external force changes the system, it will be reverted
to what is defined in the Intents the next cycle.

Each Intent has an associated [Intent Processor][2] that handles figuring out what
operations need to happen to converge into the desired state. The result of an
Intent Processor is a list of Orca Orchestrations, which can either be rendered out to
a human-friendly dry-run summary, or submitted to Orca for immediate processing. Updates
to Orca have been made to allow idempotent re-submission of Orchestrations, so Keel will
continually ask Orca to converge on the state Keel desires, but will not create duplicate
work.

Under the hood, Keel uses [Keiko][3] - the queue library used in Orca - to handle workload
distribution. There are two primary message types, 1) [ScheduleConvergence][4] and 2) 
[ConvergeIntent][5]. `ScheduleConvergence` is a singleton message that is always on the
queue. When handled by a single Keel instance, it is responsible for finding all active
Intents and scheduling individual `ConvergeIntent` messages for each. A `ConvergeIntent`
message has two TTLs: staleness & timeout, the first tracks when an Intent should refresh
desired state and the second is when the Intent should be abandoned until the next cycle.

All Intents can be submitted for [dry-run][6]. When the dry-run flag is set, human-friendly
output will be returned from the API outlining what operations would occur against the 
current system state.

Since system states can be volatile, replayability of system state is important. Keel
supports [tracing system state][7] on a per-cycle basis, allowing you to update your declarative
model and dry-run against synthetic system state to evaluate if your definitions do what
you want them to do. This tracing system can also be useful for debugging.

[1]: https://github.com/spinnaker/keel/blob/master/keel-core/src/main/kotlin/com/netflix/spinnaker/keel/Intent.kt
[2]: https://github.com/spinnaker/keel/blob/master/keel-core/src/main/kotlin/com/netflix/spinnaker/keel/IntentProcessor.kt
[3]: https://github.com/spinnaker/keiko
[4]: https://github.com/spinnaker/keel/blob/master/keel-scheduler/src/main/kotlin/com/netflix/spinnaker/keel/scheduler/handler/ScheduleConvergeHandler.kt
[5]: https://github.com/spinnaker/keel/blob/master/keel-scheduler/src/main/kotlin/com/netflix/spinnaker/keel/scheduler/handler/ConvergeIntentHandler.kt
[6]: https://github.com/spinnaker/keel/blob/master/keel-core/src/main/kotlin/com/netflix/spinnaker/keel/dryrun/DryRunIntentLauncher.kt
[7]: https://github.com/spinnaker/keel/blob/master/keel-core/src/main/kotlin/com/netflix/spinnaker/keel/tracing/TraceRepository.kt
