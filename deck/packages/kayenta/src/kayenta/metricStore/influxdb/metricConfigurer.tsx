import { get } from 'lodash';
import * as React from 'react';

import type { ICanaryMetricConfig } from '../../domain';
import MetricQueryTemplateEditor from '../../edit/metricQueryTemplateEditor';
import { templateProviderVariables } from '../../edit/templateProviderVariables';

/**
 * InfluxDB is Template-only: it gained customInlineTemplate/customFilterTemplate support as part
 * of the same change that unified every provider's query template UI, so there's no legacy
 * guided-form UI to preserve and building a new one that would be deprecated on day one isn't
 * worth it. This just renders the shared template editor directly, with InfluxDB's own available
 * variables hint.
 */
export const queryFinder = (metric: ICanaryMetricConfig) =>
  get(metric, 'query.customInlineTemplate') || get(metric, 'query.customFilterTemplate') || '';

export default function InfluxDbMetricConfigurer() {
  return <MetricQueryTemplateEditor providerVariableHints={templateProviderVariables.influxdb} />;
}
