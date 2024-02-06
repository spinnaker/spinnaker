# Frequently Asked Questions

- [Can you use Kayenta as a standalone service without the rest of Spinnaker?](#can-you-use-kayenta-as-a-standalone-service-with-out-the-rest-of-spinnaker)
- [Where are the API docs?](#where-are-the-api-docs)
- [What metric sources does Kayenta support?](#what-metric-sources-does-kayenta-support)
- [How does Kayenta decide if a metric passes or fails?](#how-does-kayenta-decide-if-a-metric-passes-or-fails)
- [How do I report metrics in a way that is compatible with Kayenta and canary analysis](#how-do-i-report-metrics-in-a-way-that-is-compatible-with-kayenta-and-canary-analysis)
- [My metric failed and I don't agree with the results, can I change how sensitive Kayenta is to change?](#my-metric-failed-and-i-dont-agree-with-the-results-can-i-change-how-sensitive-kayenta-is-to-change)
- [Why doesn't my Google account have access to get bucket metadata?](#why-doesnt-my-google-account-have-access-to-get-bucket-metadata)
- [Halyard doesn't support feature X in Kayenta, how do I use it?](#halyard-doesnt-support-feature-x-in-kayenta-how-do-I-use-it)

## Can you use Kayenta as a standalone service without the rest of Spinnaker?

Yes, Kayenta has an API that can be used to perform canary analysis outside of Spinnaker.
See [Kayenta Standalone](./kayenta-standalone.md) for more information.

## Where are the API docs?

When Kayenta is running, it serves its API docs at [http://localhost:8090/swagger-ui/index.html](http://localhost:8090/swagger-ui/index.html).

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

## Why doesn't my Google account have access to get bucket metadata?

In order for Kayenta to read and write from a GCS bucket, it first needs to checks for the existence of the bucket. It does this by making a GET request to the `storage/v1/b/{bucket}` API which returns some metadata about the bucket. In order to interact with this API you need a role that has the `storage.buckets.get` permission. This permission used to be included in Google's [Standard Roles](https://cloud.google.com/storage/docs/access-control/iam-roles), but has since been removed and put into [Legacy Roles](https://cloud.google.com/storage/docs/access-control/iam-roles#legacy-roles). In order to get that permission, you can create a custom role and apply `storage.buckets.get` to it, add the `roles/storage.legacyBucketReader` as explained in the Legacy Roles section, or use the `roles/storage.admin` role.

## Halyard doesn't support feature X in Kayenta, how do I use it?

This question comes up time to time, Kayenta is used by many companies as a standalone service outside of Spinnaker.
So when one of these companies add a feature they don't always add the corresponding support required in Halyard (or Deck).

Luckily Halyard has a mechanism for supporting features that haven't been explicitly added to halyard.

Halyard has a concept of [custom profiles](https://www.spinnaker.io/reference/halyard/custom/#custom-profiles).

The tl;dr of it is that you can create a file called `~/.hal/default/profiles/kayenta-local.yml` and add any unsupported config you want to it.

This config will be left merged into the configuration that halyard auto generates (`kayenta.yml`) 

So if I wanted to add a New Relic account to my config and Halyard didn't have explicit support for New Relic, I would add the following to `~/.hal/default/profiles/kayenta-local.yml`

```yaml
kayenta:
  newrelic:
    enabled: true
    accounts:
    - name: my-newrelic-account
      apiKey: xxxx
      applicationKey: xxxx
#      defaultScopeKey: server_scope # Optional, if omitted every request must supply the _scope_key param in extended scope params
#      defaultLocationKey: server_region # Optional, if omitted requests must supply the _location_key if it is needed.
      supportedTypes:
        - METRICS_STORE
      endpoint.baseUrl: https://insights-api.newrelic.com
```

See the [Halyard page on custom configuration](https://www.spinnaker.io/reference/halyard/custom/#custom-configuration) for more information.
