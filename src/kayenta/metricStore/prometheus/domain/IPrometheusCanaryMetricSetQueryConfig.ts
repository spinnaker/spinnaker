import { ICanaryMetricSetQueryConfig } from 'kayenta/domain';

export interface IPrometheusCanaryMetricSetQueryConfig extends ICanaryMetricSetQueryConfig {
  metricName: string;
  labelBindings: string[];
  groupByFields: string[];
}
