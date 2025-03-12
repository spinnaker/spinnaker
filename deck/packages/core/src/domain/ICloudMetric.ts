export interface ICloudMetricDescriptor {
  name: string;
  namespace: string;
  dimensions?: IMetricAlarmDimension[];
}

export interface IMetricAlarmDimension {
  name: string;
  value: string;
}

interface IDataPoint {
  timestamp: number;
  average?: number;
  sum?: number;
  minimum?: number;
  maximum?: number;
  sampleCount?: number;
}

export interface ICloudMetricStatistics {
  unit: string;
  datapoints: IDataPoint[];
}
