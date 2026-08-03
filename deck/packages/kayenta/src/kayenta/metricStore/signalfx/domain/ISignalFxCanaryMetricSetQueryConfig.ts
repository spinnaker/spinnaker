import type { ICanaryMetricSetQueryConfig } from '../../../domain';

export interface ISignalFxCanaryMetricSetQueryConfig extends ICanaryMetricSetQueryConfig {
  metricName: string;
  aggregationMethod: string;
  queryPairs: [{ key: string; value: string }];
}
