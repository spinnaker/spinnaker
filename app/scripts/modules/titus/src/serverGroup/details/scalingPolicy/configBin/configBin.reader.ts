import { IPromise } from 'angular';

import { ReactInjector, IMetricAlarmDimension } from '@spinnaker/core';

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
  public getConfig(clusterName: string): IPromise<IClusterConfig> {
    return ReactInjector.API.one('configbin', 'cloudwatch-forwarding', clusterName).get();
  }
}

export const configBinService = new ConfigBinService();
