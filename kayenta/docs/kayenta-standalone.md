# Kayenta as a Standalone API

The purpose of this doc is to cover what you need to know about running Kayenta as a standalone microservice / API.

## Prerequisites to using Kayenta's API to do Canary Analysis 

These are the minimum requirements to have a usable API that can perform canary analysis:

### Redis

Kayenta runs [Orca](https://github.com/spinnaker/orca) embedded for creating pipelines of tasks that do the actual work of canary analysis. 
Redis is the data store that Orca uses to enable this to happen in a distributed manner.

Please note that Redis must not be in clustered mode. In AWS, you can use ElastiCache in a non-clustered Redis mode.

### A properly configured Kayenta configuration file

See [Configuring Kayenta](./configuring-kayenta.md).

### The runnable Kayenta microservice

See [Building and Running Kayenta](./building-and-running-kayenta.md).

### An Application that has been instrumented to report metrics in a way usable by Kayenta

See [Instrumenting Application Metrics for Kayenta](./instrumenting-application-metrics-for-kayenta.md).

### Referee (optional)

[Referee](https://github.com/Nike-Inc/Referee) is a UI for users that is designed for the Kayenta API, but does not require the rest of Spinnaker. Referee provides a Config Generation UI, a Retrospective Analysis tool for rapidly iterating on canary configs during the on-boarding process, and a Report Viewer for the canary results produced by Kayenta.

Without Referee, users will need to create their JSON canary configuration using the schema outlined in the [canary config doc](./canary-config.md) and parse the canary results from the JSON returned by the Kayenta API themselves.

## Performing Canary Analysis

After working through the [prerequisites](#prerequisites-to-using-kayentas-api-to-do-canary-analysis), you should have a Kayenta environment up and running and be ready to do canary analysis.

### A Brief overview of Kayenta's endpoints for canary analysis

See the [full API docs](./faq.md#where-are-the-api-docs) and the code for more detailed information.

#### /config

This endpoint allows users to store created canary configs that can be referenced by name or id later when interacting with the canary or standalone canary endpoints.

#### /canary

This is the main endpoint for canary analysis. This is the endpoint that Deck and Orca use to [implement what is represented in Deck as the canary analysis stage](https://github.com/spinnaker/orca/tree/master/orca-kayenta/src/main/kotlin/com/netflix/spinnaker/orca/kayenta).
A high-level overview of the way this endpoint works is as follows:

1. You trigger a canary analysis execution via a POST call to `/canary` with the following:
 - canary config (can be an id reference to a saved config or can be the actual config embedded in the call)
 - [scope / location data for your control and experiment](./instrumenting-application-metrics-for-kayenta.md)
 - Timestamps for the start and end times for the control and experiment that you are trying to analyze
2. Kayenta starts the execution and returns an id
3. You can now poll GET `/canary/${id}` and wait for the status to be complete
4. Once complete, you can parse the results from the JSON

See the [SignalFx End to End test for the /canary endpoint](../kayenta-signalfx/src/integration-test/java/com/netflix/kayenta/signalfx/EndToEndCanaryIntegrationTests.java) for a programmatic example.

#### /standalone_canary_analysis

This endpoint is an abstraction on top of the `/canary` endpoint.
It is a port of the [Deck/Orca canary stage user experience in API form]((https://github.com/spinnaker/orca/tree/master/orca-kayenta/src/main/kotlin/com/netflix/spinnaker/orca/kayenta)).

Note: This endpoint is disabled by default. You need to explicitly enable it via your [config](./configuring-kayenta.md).

It allows a user to define a lifetime and interval length to do real-time canary analysis in multiple intervals and get an aggregated result.

A high-level overview of the way this endpoint works is as follows:
1. You trigger a canary analysis execution via a POST call to `/standalone_canary_analysis` with the following:
    - canary config (can be an id reference to a saved config or can be the actual config embedded in the call)
    - [scope / location data for your control and experiment](./instrumenting-application-metrics-for-kayenta.md)
    - a lifetime for the aggregated canary analysis
    - how often you want a `/canary` judgment to occur
    - fail fast score threshold
    - final pass score threshold
2. Kayenta starts the execution and returns an id
3. You can now poll GET `/standalone_canary_analysis/${id}` and wait for the status to be complete
4. Once complete, you can parse the results from the JSON

See the [SignalFx End to End test for the /standalone_canary_analysis endpoint](../kayenta-signalfx/src/integration-test/java/com/netflix/kayenta/signalfx/EndToEndStandaloneCanaryAnalysisIntegrationTests.java) for a programmatic example.
