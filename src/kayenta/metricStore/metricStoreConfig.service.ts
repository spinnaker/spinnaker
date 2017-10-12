import * as React from 'react';
import { ICanaryMetricConfig } from 'kayenta/domain/ICanaryConfig';

export interface IMetricStoreConfig {
  name: string;
  metricConfigurer: React.ComponentClass;
  queryFinder: (metric: ICanaryMetricConfig) => string;
}

export class MetricStoreConfigService {

  private configs: IMetricStoreConfig[] = [];

  public register(config: IMetricStoreConfig): void {
    this.configs.push(config);
  }

  public getConfig(storeName: string): IMetricStoreConfig {
    return this.configs.find(config => config.name === storeName);
  }
}

export default new MetricStoreConfigService();
