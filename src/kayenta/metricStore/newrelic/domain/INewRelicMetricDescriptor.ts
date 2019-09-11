import { IMetricsServiceMetadata } from 'kayenta/domain/IMetricsServiceMetadata';

export interface INewRelicMetricDescriptor extends IMetricsServiceMetadata {
  name: string;
}
