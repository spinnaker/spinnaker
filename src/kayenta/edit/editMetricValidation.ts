import { get } from 'lodash';
import { ICanaryMetricConfig } from '../domain';

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

  const isNameUnique = metricList.every(m => m.name !== editingMetricName || m.id === editingMetric.id);
  if (!isNameUnique) {
    nextErrors.name = { message: `Metric '${editingMetricName}' already exists` };
  }

  return nextErrors;
}

export function validateMetric(
  editingMetric: ICanaryMetricConfig,
  metricList: ICanaryMetricConfig[],
): ICanaryMetricValidationErrors {
  const errors: ICanaryMetricValidationErrors = {
    name: null,
  };

  return [validateMetricName].reduce(
    (reducedErrors, validator) => validator(reducedErrors, editingMetric, metricList),
    errors,
  );
}
