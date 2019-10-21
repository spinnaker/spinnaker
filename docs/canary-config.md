Objects in this document are documented using [Markdown Syntax for Object Notation (MSON)].

### Canary Config Object model (object)

#### Properties
- `id` **some-custom-id** (string, optional) - If not supplied a GUID will be generated for you. However you can supply a custom string here. The id is used when you call Kayenta to trigger canary execution, if you do not want to supply the config as part of the request.
- `name` **my-app golden signals canary config** (string, required) - Name for canary configuration.
- `description` **Canary config for my-app** (string, required) - Description for the canary configuration.
- `applications` (array[string], required) - A list of applications that the canary is for. You can just have a list with single item `ad-hoc` as the entry, unless you are storing the configuration in Kayenta and sharing it. 
- `judge` ([CanaryJudgeConfig](#canary-judge-config), required) - Judge configuration.
- `metrics` (array([CanaryMetricConfig](#canary-metric-config))) - List of metrics to analyze.
- `templates` (map<string, string>, optional) - Templates allow you to compose and parameterize advanced queries against your telemetry provider. Parameterized queries are hydrated by values provided in the canary stage. The <strong>project</strong>, <strong>resourceType</strong>, </string><strong>scope</strong>, and <strong>location</strong> variable bindings are implicitly available. For example, you can interpolate <strong>project</strong> using the following syntax: <strong>\${project}</strong>.
- `classifier` ([CanaryClassifierConfig](#canary-classifier-config), required) - The classification configuration, such as group weights.

<a name="canary-judge-config"></a>
### CanaryJudgeConfig (object)
Currently there is one judge and this object should be static across all the configuration (see the above examples).
#### Properties
- `name` **NetflixACAJudge-v1.0** (string, required) - Judge to use, as of right now there is only `NetflixACAJudge-v1.0`.
- `judgeConfigurations` **{}** (object, required) - Map<string, object> of judgement configuration, this should always be an empty object as of right now.

<a name="canary-metric-config"></a>
### CanaryMetricConfig (object)
Describes a metric that will be used in determining the health of a canary.
#### Properties
- `name` **http errors** (string, required) - Human readable name of the metric under test.
- `query` (enum[[CanaryMetricSetQueryConfig](#canary-metrics-set-query-config)], required) - Query config object for your metric source type.
- `groups` (array[string], required) - List of metrics groups that this metric will belong to.
- `analysisConfigurations` ([AnalysisConfiguration](#analysis-configuration), required) - Analysis configuration, describes how to judge a given metric.
- `scopeName` (enum[string], required)
    - `default` - only accepted value here

<a name="canary-metrics-set-query-config"></a>
### CanaryMetricSetQueryConfig (object)
Metric source interface for describing how to query for a given metric / metric source.
#### Properties
- One of
    - AtlasCanaryMetricSetQueryConfig
    - DatadogCanaryMetricSetQueryConfig
    - GraphiteCanaryMetricSetQueryConfig
    - InfluxdbCanaryMetricSetQueryConfig
    - [NewRelicInsightsCanaryMetricSetQueryConfig](../kayenta-newrelic-insights/docs/metric-set-query-config.md)
    - PrometheusCanaryMetricSetQueryConfig
    - [SignalFxCanaryMetricSetQueryConfig](../kayenta-signalfx/docs/metric-set-query-config.md)
    - StackdriverCanaryMetricSetQueryConfig
    - WavefrontCanaryMetricSetQueryConfig
        
<a name="analysis-configuration"></a>
### AnalysisConfiguration (object)
Wrapper object that includes the Canary Analysis Configuration and describes how to judge a given metric.
#### Properties
- `canary` ([CanaryAnalysisConfiguration](#canary-analysis-configuration))

<a name="canary-analysis-configuration"></a>
### CanaryAnalysisConfiguration (object)
Describes how to judge a metric, see the [Netflix Automated Canary Analysis Judge] for more information.
#### Properties
- `direction` (enum[string], required) - Which direction of statistical change triggers the metric to fail.
    - `increase` - Use when you want the canary to fail only if it is significantly higher than the baseline (error counts, memory usage, etc, where a decrease is not a failure).
    - `decrease` - Use when you want the canary to fail only if it is significantly lower than the baseline (success counts, etc, where a larger number is not a failure).
    - `either` - Use when you want the canary to fail if it is significantly higher or lower than the baseline.
- `nanStrategy` (enum[string], required) - How to handle NaN values which can occur if the metric does not return data for a particular time interval.
    - `remove` - Use when you expect a metric to always have data and you want the NaNs removed from your data set (usage metrics).
    - `replace` - Use when you expect a metric to return no data in certain use cases and you want the NaNs replaced with zeros (for example: count metrics, if no errors happened, then metric will return no data for that time interval).
- `critical` **true** (boolean, optional) - Use to fail the entire canary if this metric fails (recommended for important metrics that signal service outages or severe problems).
- `mustHaveData` **true** (boolean, optional) - Use to fail a metric if data is missing.
- `effectSize` ([EffectSize](#effect-size), optional) - Controls how much different the metric needs to be to fail or fail critically.

<a name="effect-size"></a>
### EffectSize
Controls the degree of statistical significance the metric needs to fail or fail critically. 
Metrics marked as critical can also define `criticalIncrease` and `criticalDecrease`. 
See the [Netflix Automated Canary Analysis Judge] and [Mann Whitney Classifier] classes for more information.

#### Properties
- `allowedIncrease` **1.1** (number, optional) - Defaults to 1. The multiplier increase that must be met for the metric to fail. This example makes the metric fail when the metric has increased 10% from the baseline.
- `allowedDecrease` **0.90** (number, optional) - Defaults to 1. The multiplier decrease that must be met for the metric to fail. This example makes the metric fail when the metric has decreased 10% from the baseline.
- `criticalIncrease` **5.0** (number, optional) - Defaults to 1. The multiplier increase that must be met for the metric to be a critical failure and fail the entire analysis with a score of 0. This example make the canary fail critically if there is a 5x increase.
- `criticalDecrease` **0.5** (number, optional) - Defaults to 1. The multiplier decrease that must be met for the metric to be a critical failure and fail the entire analysis with a score of 0. This example make the canary fail critically if there is a 50% decrease.

<a name="canary-classifier-config"></a>
### CanaryClassifierConfig
#### Properties
- `groupWeights` (enum[string], required)
  - `groups` **"Latency" : 33** (object, required) - List of each metrics group along with its corresponding weight. Weights must total 100.
  
<a name="links"></a>
## Links
- [Spinnaker Canary Best Practices]
- [Canary analysis: Lessons learned and best practices from Google and Waze]
- [Armory Kayenta Documentation]
- [Example Signalfx canary config]
  
[Spinnaker Canary Best Practices]: https://www.spinnaker.io/guides/user/canary/best-practices/
[Armory Kayenta Documentation]: https://docs.armory.io/spinnaker/configure_kayenta/
[Example Signalfx canary config]: https://github.com/spinnaker/kayenta/blob/master/kayenta-signalfx/metric-query-config.md
[Markdown Syntax for Object Notation (MSON)]: https://github.com/apiaryio/mson
[Canary analysis: Lessons learned and best practices from Google and Waze]: https://cloud.google.com/blog/products/devops-sre/canary-analysis-lessons-learned-and-best-practices-from-google-and-waze
[Netflix Automated Canary Analysis Judge]: https://github.com/spinnaker/kayenta/blob/master/kayenta-judge/src/main/scala/com/netflix/kayenta/judge/NetflixACAJudge.scala
[Mann Whitney Classifier]: https://github.com/spinnaker/kayenta/blob/master/kayenta-judge/src/main/scala/com/netflix/kayenta/judge/classifiers/metric/MannWhitneyClassifier.scala
