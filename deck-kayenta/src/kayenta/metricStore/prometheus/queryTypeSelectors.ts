import { ICanaryMetricConfig } from '../../domain';
import { PrometheusQueryType } from './domain/IPrometheusCanaryMetricSetQueryConfig';
import { ITemplateTransformFunctions } from '../../selectors/filterTemplatesSelectors';

const PROMQL_PREFIX = 'PromQL:';

export function isQueryPromQL(template: string): boolean {
  return template != null && template.startsWith(PROMQL_PREFIX);
}

export function removePromQLPrefix(template: string): string {
  if (isQueryPromQL(template)) {
    return template.substring(PROMQL_PREFIX.length);
  }
  return template;
}

export function appendPromQLPrefix(template: string): string {
  return `${PROMQL_PREFIX}${template}`;
}

export const getPrometheusQueryType = (editingMetric: ICanaryMetricConfig) => {
  if (editingMetric.query.queryType) {
    return editingMetric.query.queryType;
  }
  const inlineTemplate = editingMetric.query.customInlineTemplate;
  if (isQueryPromQL(inlineTemplate)) {
    return PrometheusQueryType.PROMQL;
  }
  return PrometheusQueryType.DEFAULT;
};

export const prometheusQueryTypeToTransformFunction: { [queryType: string]: ITemplateTransformFunctions } = {
  [PrometheusQueryType.PROMQL]: {
    fromValue: removePromQLPrefix,
    toValue: appendPromQLPrefix,
  },
};
