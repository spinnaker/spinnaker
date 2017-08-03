import * as React from 'react';

export interface IMetricStoreConfig {
  name: string;
  metricConfigurer: React.ComponentClass;
}

class MetricStoreConfigService {

  private configs: IMetricStoreConfig[] = [];

  public register(config: IMetricStoreConfig): void {
    this.configs.push(config);
  }

  public getConfig(storeName: string): IMetricStoreConfig {
    return this.configs.find(config => config.name === storeName);
  }
}

export default new MetricStoreConfigService();
