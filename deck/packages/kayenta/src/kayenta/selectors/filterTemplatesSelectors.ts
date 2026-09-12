import { get, identity, isEmpty } from 'lodash';
import { createSelector } from 'reselect';

import type { ICanaryState } from '../reducers';

export const inlineTemplateValueSelector = (state: ICanaryState): string =>
  get(state, 'selectedConfig.editingMetric.query.template');

// No provider currently needs to transform a template's value between how it's persisted and how
// it's displayed in the UI. (Prometheus previously used a client-only "PromQL:" string prefix to
// distinguish PromQL-mode templates, but nothing server-side ever parsed for that prefix -- see
// kayenta-prometheus's PrometheusMetricsService.java/PrometheusCanaryMetricSetQueryConfig.java --
// so it was dead weight once the template editor became available to every provider, and was
// retired.) These stay as pass-through selectors so MetricQueryTemplateEditor doesn't need to
// special-case a provider that might need a real transform in the future.
export const transformInlineTemplateForDisplay = (_state: ICanaryState): ((template: string) => string) => identity;

export const transformInlineTemplateForSave = (_state: ICanaryState): ((template: string) => string) => identity;

export const isInlineTemplateValidSelector = createSelector(inlineTemplateValueSelector, (value) => !isEmpty(value));
