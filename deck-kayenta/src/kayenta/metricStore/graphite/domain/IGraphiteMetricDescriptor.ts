import { IMetricsServiceMetadata } from 'kayenta/domain';

export interface IGraphiteMetricDescriptor extends IMetricsServiceMetadata {
  name: string;
}
