import { IMetricsServiceMetadata } from 'kayenta/domain/IMetricsServiceMetadata';

export interface IDatadogMetricDescriptor extends IMetricsServiceMetadata {
  name: string;
}
