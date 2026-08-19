import { get, identity, isEmpty } from 'lodash';
import { createSelector } from 'reselect';

import { configTemplatesSelector, editingMetricSelector, editingTemplateSelector, metricListSelector } from './';
import { validateTemplate } from '../edit/filterTemplatesValidation';
import type { ICanaryState } from '../reducers';

export const selectedTemplateNameSelector = (state: ICanaryState): string =>
  get(state, 'selectedConfig.editingMetric.query.customFilterTemplate');

export const inlineTemplateValueSelector = (state: ICanaryState): string =>
  get(state, 'selectedConfig.editingMetric.query.customInlineTemplate');

// No provider currently needs to transform a template's value between how it's persisted and how
// it's displayed in the UI. (Prometheus previously used a client-only "PromQL:" string prefix to
// distinguish PromQL-mode templates, but nothing server-side ever parsed for that prefix -- see
// kayenta-prometheus's PrometheusMetricsService.java/PrometheusCanaryMetricSetQueryConfig.java --
// so it was dead weight once the template editor became available to every provider, and was
// retired.) These stay as pass-through selectors so MetricQueryTemplateEditor doesn't need to
// special-case a provider that might need a real transform in the future.
export const transformInlineTemplateForDisplay = (_state: ICanaryState): ((template: string) => string) => identity;

export const transformInlineTemplateForSave = (_state: ICanaryState): ((template: string) => string) => identity;

export const editingTemplateValidationSelector = createSelector(
  editingTemplateSelector,
  configTemplatesSelector,
  metricListSelector,
  editingMetricSelector,
  (editingTemplate, configTemplates, metricList, editingMetric) =>
    validateTemplate({
      editingTemplate,
      configTemplates,
      metricList,
      editingMetric,
    }),
);

export const isFilterTemplateValidSelector = createSelector(editingTemplateValidationSelector, (validation) =>
  isEmpty(Object.keys(validation.errors)),
);

export const isInlineTemplateValidSelector = createSelector(inlineTemplateValueSelector, (value) => !isEmpty(value));

// This only ever gated the Confirm button on the "editingTemplate" name/value form's own
// validation (duplicate/blank name, blank value) -- not on whether a template is actually
// selected/populated, since most metrics never touch that form at all. Kept as its own named
// selector (rather than inlining isFilterTemplateValidSelector at call sites) since it's the one
// editMetricModal.tsx actually gates Confirm on.
export const isTemplateValidSelector = isFilterTemplateValidSelector;
