import type { IMetricsServiceMetadata } from '../../../domain/IMetricsServiceMetadata';

export interface IDatadogMetricDescriptor extends IMetricsServiceMetadata {
  name: string;
}
