import type { IMetricsServiceMetadata } from '../../../domain/IMetricsServiceMetadata';

export interface INewRelicMetricDescriptor extends IMetricsServiceMetadata {
  name: string;
}
