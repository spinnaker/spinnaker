import { IMetricsServiceMetadata } from 'kayenta/domain/IMetricsServiceMetadata';

export interface IStackdriverMetricDescriptor extends IMetricsServiceMetadata {
  metricKind: string;
  labels: IStackdriverMetricDescriptorLabels[];
  name: string;
  displayName: string;
  type: string;
  description: string;
  unit: string;
  valueType: string;
}

export interface IStackdriverMetricDescriptorLabels {
  description: string;
  key: string;
}
