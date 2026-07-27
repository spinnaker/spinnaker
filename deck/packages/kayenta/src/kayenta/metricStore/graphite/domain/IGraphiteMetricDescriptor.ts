import type { IMetricsServiceMetadata } from '../../../domain';

export interface IGraphiteMetricDescriptor extends IMetricsServiceMetadata {
  name: string;
}
