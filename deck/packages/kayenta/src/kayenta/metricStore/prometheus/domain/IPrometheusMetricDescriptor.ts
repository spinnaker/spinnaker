import type { IMetricsServiceMetadata } from '../../../domain/IMetricsServiceMetadata';

export interface IPrometheusMetricDescriptor extends IMetricsServiceMetadata {
  name: string;
}
