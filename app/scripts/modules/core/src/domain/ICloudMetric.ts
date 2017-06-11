export interface ICloudMetricDescriptor {
  name: string;
  namespace: string;
  dimensions?: IMetricAlarmDimension[];
}

export interface IMetricAlarmDimension {
  name: string;
  value: string;
}

export interface ICloudMetricStatistics {
  unit: string;
  datapoints: any[];
}
