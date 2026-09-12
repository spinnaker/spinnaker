import { get } from 'lodash';
import * as React from 'react';

import type { ICanaryMetricConfig } from '../../domain';
import MetricQueryTemplateEditor from '../../edit/metricQueryTemplateEditor';
import FormRow from '../../layout/formRow';

export const queryFinder = (metric: ICanaryMetricConfig) => get(metric, 'query.template') || '';

const EXAMPLE_QUERIES: { [label: string]: string } = {
  Gauge: `SELECT avg(Value)
FROM otel_metrics_gauge
WHERE MetricName = 'requests.latency'
  AND ResourceAttributes['deployment.id'] = '\${scope}'
  AND TimeUnix BETWEEN toDateTime(\${startEpochSeconds}) AND toDateTime(\${endEpochSeconds})
GROUP BY toStartOfInterval(TimeUnix, INTERVAL \${step} SECOND)
ORDER BY 1`,
  Sum: `SELECT sum(Value)
FROM otel_metrics_sum
WHERE MetricName = 'requests.count'
  AND ResourceAttributes['deployment.id'] = '\${scope}'
  AND TimeUnix BETWEEN toDateTime(\${startEpochSeconds}) AND toDateTime(\${endEpochSeconds})
GROUP BY toStartOfInterval(TimeUnix, INTERVAL \${step} SECOND)
ORDER BY 1`,
  Histogram: `SELECT quantile(0.99)(arrayJoin(BucketCounts))
FROM otel_metrics_histogram
WHERE MetricName = 'requests.latency'
  AND ResourceAttributes['deployment.id'] = '\${scope}'
  AND TimeUnix BETWEEN toDateTime(\${startEpochSeconds}) AND toDateTime(\${endEpochSeconds})
GROUP BY toStartOfInterval(TimeUnix, INTERVAL \${step} SECOND)
ORDER BY 1`,
};

/*
 * Clickhouse has no structured/guided query builder - every metric is defined entirely by the SQL
 * template edited via the shared query template editor below. This component renders that shared
 * editor, preceded by Clickhouse-specific documentation of the template variables Kayenta will
 * substitute, the shape the query must return, and copy-paste starting points for the
 * OpenTelemetry Collector's Clickhouse exporter schema. Ad-hoc SQL against any other schema is
 * equally supported - just replace the example text with your own query.
 */
export default function ClickhouseMetricConfigurer() {
  return (
    <>
      <FormRow label="Clickhouse Query" inputOnly={true}>
        <p className="body-small color-text-caption">
          Define this metric&apos;s query in the SQL template field below. Available template variables:{' '}
          <code>{'${scope}'}</code>, <code>{'${location}'}</code>, <code>{'${step}'}</code>,{' '}
          <code>{'${startEpochSeconds}'}</code>, and <code>{'${endEpochSeconds}'}</code>. The query must return a
          single numeric column with one row per step, ordered by time ascending - Kayenta does not re-bin or
          gap-fill results.
        </p>
        {Object.entries(EXAMPLE_QUERIES).map(([label, query]) => (
          <div key={label} style={{ marginBottom: '10px' }}>
            <div className="body-small color-text-caption">{label} example (OpenTelemetry Collector schema):</div>
            <pre style={{ whiteSpace: 'pre-wrap' }}>{query}</pre>
          </div>
        ))}
      </FormRow>
      <MetricQueryTemplateEditor />
    </>
  );
}
