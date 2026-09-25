import { get } from 'lodash';
import * as React from 'react';

import type { ICanaryMetricConfig } from '../../domain';
import MetricQueryTemplateEditor from '../../edit/metricQueryTemplateEditor';
import { templateProviderVariables } from '../../edit/templateProviderVariables';

/**
 * Wavefront is Template-only: it gained query.template support as part of the same change that
 * unified every provider's query template UI, so there's no legacy guided-form UI to preserve and
 * building a new one that would be deprecated on day one isn't worth it. This just renders the
 * shared template editor directly, with Wavefront's own available variables hint.
 */
export const queryFinder = (metric: ICanaryMetricConfig) => get(metric, 'query.template') || '';

export default function WavefrontMetricConfigurer() {
  return <MetricQueryTemplateEditor providerVariableHints={templateProviderVariables.wavefront} />;
}
