import { HelpContentsRegistry } from '@spinnaker/core';

const helpContents: { [key: string]: string } = {
  'pipeline.config.canary.analysisType': `
    <p>
      <strong>Real Time</strong> analysis will be performed
      over a time interval beginning at the moment of execution.
      <ul>
        <li>
          <strong>Automatic:</strong> Spinnaker will provision and clean up the baseline and canary server groups. Not all cloud providers support this mode.
        </li>
        <li>
          <strong>Manual:</strong> You are responsible for provisioning and cleaning up the baseline and canary server groups.
        </li>
      </ul>
    </p>
    <p>
      <strong>Retrospective</strong> analysis will be performed over an explicitly-specified time
      interval (likely in the past). You are responsible for provisioning and cleaning up
      the baseline and canary server groups.
    </p>
  `,
  'pipeline.config.canary.delayBeforeAnalysis':
    '<p>The number of minutes until the first canary analysis measurement interval begins.</p>',
  'pipeline.config.canary.baselineAnalysisOffset':
    '<p>The offset in minutes of baseline data collection from the beginning of canary analysis. Useful for comparing metrics pre-canary for relative comparison. This field accepts SpEL</p>',
  'pipeline.config.canary.canaryInterval': `
        <p>The frequency at which a canary score is generated. The recommended interval is at least 30 minutes.</p>
        <p>If an interval is not specified, or the specified interval is larger than the overall time range, there will be one canary run over the full time range.</p>`,
  'pipeline.config.canary.successfulScore':
    '<p>The minimum score the canary must achieve to be considered successful.</p>',
  'pipeline.config.canary.unhealthyScore':
    '<p>The lowest score the canary can attain before it is aborted and disabled as a failure.</p>',
  'pipeline.config.canary.baselineGroup':
    '<p>The server group to treat as the <em>control</em> in the canary analysis.</p>',
  'pipeline.config.canary.baselineLocation':
    '<p>The location (could be a region, a namespace, or something else) of the server group to treat as the <em>control</em> in the canary analysis.</p>',
  'pipeline.config.canary.canaryGroup':
    '<p>The server group to treat as the <em>experiment</em> in the canary analysis.</p>',
  'pipeline.config.canary.canaryLocation':
    '<p>The location (could be a region, a namespace, or something else) of the server group to treat as the <em>experiment</em> in the canary analysis.</p>',
  'pipeline.config.canary.startTimeIso':
    '<p>The overall start time of the data points to be retrieved, specified as a UTC instant using <a target="_" href="https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html#ISO_INSTANT">ISO-8601 instant format.</a> For example, `2018-07-12T20:28:29Z`.</p>',
  'pipeline.config.canary.endTimeIso':
    '<p>The overall end time of the data points to be retrieved, specified as a UTC instant using <a target="_" href="https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html#ISO_INSTANT">ISO-8601 instant format.</a> For example, `2018-07-12T22:28:29Z`.</p>',
  'pipeline.config.canary.extendedScopeParams': `
        <p>Metric source specific parameters which may be used to further alter the canary scope.</p>
        <p>Also used to provide variable bindings for use in the expansion of custom filter templates within the canary config.</p>`,
  'pipeline.config.canary.lookback':
    '<p>With an analysis type of <strong>Growing</strong>, the entire duration of the canary will be considered during the analysis.</p><p>When choosing <strong>Sliding</strong>, the canary will use the most recent number of specified minutes for its analysis report (<b>useful for long running canaries that span multiple days</b>).</p>',
  'pipeline.config.canary.delayBeforeCleanup':
    '<p>The total time after canary analysis ends before canary cluster cleanup begins. Allows for manual inspection of instances.</p>',
  'pipeline.config.canary.marginalScore': `
    <p>A canary stage can include multiple canary runs.</p>
    <p>If a given canary run score is less than or equal to the marginal threshold, the canary stage will fail immediately.</p>
    <p>If the canary run score is greater than the marginal threshold, the canary stage will not fail and will execute the remaining downstream canary runs.</p>`,
  'pipeline.config.canary.passingScore':
    '<p>When all canary runs in a stage have executed, a canary stage is considered a success if the final (that is, the latest) canary run score is greater than or equal to the pass threshold. Otherwise, it is a failure.</p>',
  'pipeline.config.canary.lifetime': `
    <p>The total time for which data will be collected and analyzed during this stage.</p>
  `,
  'pipeline.config.canary.legacySiteLocalRecipients':
    '<p>Email addresses to be notified when a canary report completes, separated by commas.</p>',
  'pipeline.config.metricsAccount':
    "<p>The account to be used to access the metric store defined in this stage's canary config.</p>",
  'pipeline.config.storageAccount':
    '<p>The account to be used to access a storage service, which will be used to store artifacts generated by this stage.</p>',
  'canary.config.metricGroupWeights': `
    <p>A canary score is the weighted sum of metric group scores.</p>
    <p>Group weights must sum to 100.</p>
  `,
  'canary.config.nanStrategy': `
    <p>When there is no value for a metric at a given point in time, it can either be ignored or assumed to be zero. The right choice depends on what is being measured. For example, when measuring successful attempts (like health checks) replacing missing values with zero may be appropriate.</p>
    <p>The default strategy for a given metric will be used if no strategy is selected.</p>
  `,
  'canary.config.filterTemplate': `
    <p>Templates allow you to compose and parameterize advanced queries against your telemetry provider.</p>
    <p>Parameterized queries are hydrated by values provided in the canary stage. The <strong>project</strong>, <strong>resourceType</strong>, </string><strong>scope</strong>, and <strong>location</strong> variable bindings are implicitly available.</p>
    <p>For example, you can interpolate <strong>project</strong> using the following syntax: <strong>\${project}</strong>.</p>
  `,
  'canary.config.signalFx.queryPairs': `
    <p><strong>Query pairs are optional</strong></p>
    <p>Can be dimensions, properties, or tags (Use tag as key for tags).</p>
    <p>
        Example: Given a metric with name <pre>'request.count'</pre> that gets reported with dimensions uri, and stats_code. </br>
        If I added the following query pairs</br>
        <pre>{
  "uri": "v1/some-endpoint",
  "status_code": "5*"
}</pre>
        I could make a metric that tracks the number of server errors for endpoint <pre>/v1/some-endpoint</pre>
    </p>
    <p>These k,v pairs are used to construct filters for the compiled SignalFlow program. EX:<pre>data('request.count', filters=filter('uri', 'v1/some-endpoint') and filter('status_code', '5*') and filter('version', '1.0.0') and filter('environment', 'production')).sum(by=['version', 'environment']).publish()</pre>
        Note that the version and environment would come from the canary scope and the sum method comes from the aggregation method.
    </p>
  `,
  'canary.config.signalFx.aggregationMethod': `
    <p>This must be a method defined in the <a target="_blank" href="https://developers.signalfx.com/reference#signalflow-stream-methods-1">SignalFlow Stream Methods</a> that supports aggregation</p>
    <p>These are methods such as min, max, sum, mean and are documented with supporting the <strong>'by'</strong> keyword, ex: <pre>sum(by=['version', 'environment'])</pre></p>
    <p>Simply put the name of the method that should be used in this field such as sum, Kayenta will populate the by clauses using the canary scope filter keys</p>
    <p>This method is used to construct the compiled SignalFlow program. EX:<pre>data('request.count', filters=filter('uri', 'v1/some-endpoint') and filter('status_code', '5*') and filter('version', '1.0.0') and filter('environment', 'production')).sum(by=['version', 'environment']).publish()</pre>
        Note that the version and environment k,v pairs are sourced from the canary scope. The other k,v pairs come from the metric specific k,v pair list.
    </p>
  `,
  'canary.config.prometheus.queryType': `
    <p>Select <strong>default</strong> to use options from the UI to configure your query.</p>
    <p>Select <strong>PromQL</strong> to compose a custom PromQL query (see <a target="blank" href="https://prometheus.io/docs/prometheus/latest/querying/basics/">documentation</a>).</p>
  `,
  // These come (almost) verbatim from Stackdriver's Metric Explorer.
  'stackdriver.resourceType':
    'For Stackdriver, a set of time series is identified by a <strong>resource type</strong> and a metric type that has data from that resource type.',
  'stackdriver.metricType':
    'For Stackdriver, a set of time series is identified by a resource type and a <strong>metric type</strong> that has data from that resource type.',
  'stackdriver.groupBy': 'Group by resource or metric labels to reduce the number of time series.',
  'stackdriver.crossSeriesReducer': 'Use an algorithm to group multiple time series together.',
  'stackdriver.perSeriesAligner': 'Use an algorithm to align individual time series.',
};

Object.keys(helpContents).forEach(key => HelpContentsRegistry.register(key, helpContents[key]));
