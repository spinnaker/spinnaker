import { IPromise } from 'angular';

import { API, IMetricAlarmDimension } from '@spinnaker/core';

export interface IClusterConfigExpression {
  account: string;
  atlasUri: string;
  comment?: string;
  metricName: string;
  dimensions: IMetricAlarmDimension[];
}

export interface IClusterConfig {
  email: string;
  expressions: IClusterConfigExpression[];
}

export class ConfigBinService {
  public static getConfig(clusterName: string): IPromise<IClusterConfig> {
    return API.one('configbin', 'cloudwatch-forwarding', clusterName).get();
  }
}
