import type { ICanaryMetricSetQueryConfig } from '../../../domain';

export interface IPrometheusCanaryMetricSetQueryConfig extends ICanaryMetricSetQueryConfig {
  resourceType: string;
  metricName: string;
  labelBindings: string[];
  groupByFields: string[];
}
