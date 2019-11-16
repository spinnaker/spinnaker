# Frequently Asked Questions

- [Can you use Kayenta as a standalone service without the rest of Spinnaker?](#can-you-use-kayenta-as-a-standalone-service-with-out-the-rest-of-spinnaker)
- [Where are the API docs?](#where-are-the-api-docs)
- [What metric sources does Kayenta support?](#what-metric-sources-does-kayenta-support)
- [How does Kayenta decide if a metric passes or fails?](#how-does-kayenta-decide-if-a-metric-passes-or-fails)
- [How do I report metrics in a way that is compatible with Kayenta and canary analysis](#how-do-i-report-metrics-in-a-way-that-is-compatible-with-kayenta-and-canary-analysis)
- [My metric failed and I don't agree with the results, can I change how sensitive Kayenta is to change?](#my-metric-failed-and-i-dont-agree-with-the-results-can-i-change-how-sensitive-kayenta-is-to-change)

## Can you use Kayenta as a standalone service without the rest of Spinnaker?

Yes, Kayenta has an API that can be used to perform canary analysis outside of Spinnaker.
See [Kayenta Standalone](./kayenta-standalone.md) for more information.

## Where are the API docs?

When Kayenta is running, it serves its API docs at [http://localhost:8090/swagger-ui.html](http://localhost:8090/swagger-ui.html).

You can control what endpoints show up on that page via the [swagger config](../kayenta-web/config/kayenta.yml) section of the main config.
<!-- TODO explain how this is controlled in the yaml. -->
<!-- TODO add a cheat link to a generated yaml and add my postman collection. -->

## What metric sources does Kayenta support?

- [Atlas](https://github.com/spinnaker/kayenta/tree/master/kayenta-atlas)
- [Datadog](https://github.com/spinnaker/kayenta/tree/master/kayenta-datadog)
- [Graphite](https://github.com/spinnaker/kayenta/tree/master/kayenta-graphite)
- [Influx DB](https://github.com/spinnaker/kayenta/tree/master/kayenta-influxdb)
- [New Relic Insights](https://github.com/spinnaker/kayenta/blob/master/kayenta-newrelic-insights/README.md)
- [Prometheus](https://github.com/spinnaker/kayenta/tree/master/kayenta-prometheus)
- [SignalFx](https://github.com/spinnaker/kayenta/blob/master/kayenta-signalfx/README.md)
- [Stackdriver](https://github.com/spinnaker/kayenta/tree/master/kayenta-stackdriver)
- [Wavefront](https://github.com/spinnaker/kayenta/tree/master/kayenta-wavefront)

This list may not encompass all current metric sources. [See the services that implement MetricService for current info.](https://github.com/spinnaker/kayenta/search?q=%22implements+MetricsService%22&unscoped_q=%22implements+MetricsService%22)

## How does Kayenta decide if a metric passes or fails?

Metric decisions are handled by the [judge](./canary-config.md#canary-judge-config) that you use.

### [NetflixACAJudge-v1.0](https://github.com/spinnaker/kayenta/blob/master/kayenta-judge/src/main/scala/com/netflix/kayenta/judge/NetflixACAJudge.scala)

This judge takes in metrics from a control group and an experiment group. It uses your [configuration settings](./canary-config.md#canary-analysis-configuration) to then [transform](https://github.com/spinnaker/kayenta/blob/master/kayenta-judge/src/main/scala/com/netflix/kayenta/judge/preprocessing/Transforms.scala) the data and apply the Mann Whitney U statistical test to determine the results.

See [Spinnaker Judge Docs](https://www.spinnaker.io/guides/user/canary/judge/) for additional information.

## How do I report metrics in a way that is compatible with Kayenta and canary analysis?

See [Instrumenting Application Metrics For Kayenta](./instrumenting-application-metrics-for-kayenta.md).

## My metric failed and I don't agree with the results, can I change how sensitive Kayenta is to change?

_Warning: This is a beta feature and may be removed._

Yes, there are a couple of settings available for you.
See [EffectSize](./canary-config.md#effect-size) for more information.
