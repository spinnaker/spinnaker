import { get, isEmpty } from 'lodash';
import type { ICanaryMetricConfig } from '../domain';

export interface ICanaryMetricValidationError {
  message: string;
}

export interface ICanaryMetricValidationErrors {
  [key: string]: ICanaryMetricValidationError;
}
export type MetricValidatorFunction = (
  errors: ICanaryMetricValidationErrors,
  editingMetric: ICanaryMetricConfig,
  metricList?: ICanaryMetricConfig[],
) => ICanaryMetricValidationErrors;

export function validateMetricName(
  errors: ICanaryMetricValidationErrors,
  editingMetric: ICanaryMetricConfig,
  metricList: ICanaryMetricConfig[],
): ICanaryMetricValidationErrors {
  const nextErrors = { ...errors };

  const editingMetricName = get(editingMetric, 'name', '');
  if (!editingMetricName) {
    nextErrors.name = { message: 'Name is required' };
    return nextErrors;
  }

  const isNameUnique = metricList.every((m) => m.name !== editingMetricName || m.id === editingMetric.id);
  if (!isNameUnique) {
    nextErrors.name = { message: `Metric '${editingMetricName}' already exists` };
  }

  return nextErrors;
}

const NON_QUERY_CONTENT_KEYS = ['type', 'serviceType'];

/**
 * A metric must actually query something: whether it was authored as a template or with the
 * provider's guided fields, its query has to carry more than just the provider type markers.
 */
export function validateMetricQuery(
  errors: ICanaryMetricValidationErrors,
  editingMetric: ICanaryMetricConfig,
): ICanaryMetricValidationErrors {
  const query: { [key: string]: any } = get(editingMetric, 'query') || {};
  const hasQueryContent = Object.keys(query).some(
    (key) => !NON_QUERY_CONTENT_KEYS.includes(key) && !isEmpty(query[key]) && query[key] !== false,
  );

  return hasQueryContent ? errors : { ...errors, query: { message: 'A query template or query fields are required' } };
}

export function validateMetric(
  editingMetric: ICanaryMetricConfig,
  metricList: ICanaryMetricConfig[],
): ICanaryMetricValidationErrors {
  const errors: ICanaryMetricValidationErrors = {
    name: null,
    query: null,
  };

  return [validateMetricName, validateMetricQuery].reduce(
    (reducedErrors, validator) => validator(reducedErrors, editingMetric, metricList),
    errors,
  );
}
