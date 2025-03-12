import { IMetricsServiceMetadata } from 'kayenta/domain/IMetricsServiceMetadata';

export interface IPrometheusMetricDescriptor extends IMetricsServiceMetadata {
  name: string;
}
