import { validateTemplate } from 'kayenta/edit/filterTemplatesValidation';
import { PrometheusQueryType } from 'kayenta/metricStore/prometheus/domain/IPrometheusCanaryMetricSetQueryConfig';
import {
  getPrometheusQueryType,
  prometheusQueryTypeToTransformFunction,
} from 'kayenta/metricStore/prometheus/queryTypeSelectors';
import { ICanaryState } from 'kayenta/reducers';
import {
  configTemplatesSelector,
  editingMetricSelector,
  editingTemplateSelector,
  metricListSelector,
} from 'kayenta/selectors';
import { get, identity, isEmpty } from 'lodash';
import { createSelector } from 'reselect';

// TODO(mneterval): More elegant separation of prometheus-specific logic

export interface ITemplateTransformFunctions {
  fromValue: (template: string) => string;
  toValue: (template: string) => string;
}

// Map of provider-specific query type to methods that transform templates between how they are persisted and displayed in the UI
export const queryTypeToTransformFunctions: { [queryType: string]: ITemplateTransformFunctions } = {
  ...prometheusQueryTypeToTransformFunction,
};

export const queryTypeSelector = createSelector(
  (state: ICanaryState) => state.selectedConfig.editingMetric,
  (editingMetric) => {
    return editingMetric && editingMetric.query.serviceType === 'prometheus'
      ? getPrometheusQueryType(editingMetric)
      : null;
  },
);

export const transformInlineTemplateForDisplay = createSelector(queryTypeSelector, (queryType) =>
  get(queryTypeToTransformFunctions, [queryType, 'fromValue'], identity),
);

export const transformInlineTemplateForSave = createSelector(queryTypeSelector, (queryType) =>
  get(queryTypeToTransformFunctions, [queryType, 'toValue'], identity),
);

export const selectedTemplateNameSelector = (state: ICanaryState): string =>
  get(state, 'selectedConfig.editingMetric.query.customFilterTemplate');

export const inlineTemplateValueSelector = createSelector(
  (state: ICanaryState) => get(state, 'selectedConfig.editingMetric.query.customInlineTemplate'),
  transformInlineTemplateForDisplay,
  (template: string, transformer: (template: string) => string): string => transformer(template),
);

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

const inlineTemplateQueryTypes = [PrometheusQueryType.PROMQL];

export const useInlineTemplateEditorSelector = createSelector(queryTypeSelector, (queryType) =>
  inlineTemplateQueryTypes.includes(queryType),
);

export const isTemplateValidSelector = createSelector(
  useInlineTemplateEditorSelector,
  isFilterTemplateValidSelector,
  isInlineTemplateValidSelector,
  (isInlineTemplate, isFilterTemplateValid, isInlineTemplateValid) =>
    isInlineTemplate ? isInlineTemplateValid : isFilterTemplateValid,
);
