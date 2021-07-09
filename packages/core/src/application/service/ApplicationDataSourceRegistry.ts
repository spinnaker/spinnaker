import { cloneDeep } from 'lodash';

import { IDataSourceConfig } from './applicationDataSource';

export class ApplicationDataSourceRegistry {
  private static defaultDataSourceOrder: string[] = [
    'environments',
    'serverGroups',
    'executions',
    'loadBalancers',
    'securityGroups',
    'tasks',
    'config',
  ];
  private static dataSources: Array<IDataSourceConfig<any>> = [];
  private static dataSourceOrder: string[] = [];

  public static setDataSourceOrder(keys: string[]): void {
    this.dataSourceOrder = keys;
    this.sortDataSources();
  }

  private static sortDataSources(): void {
    let order = this.defaultDataSourceOrder;
    if (this.dataSourceOrder.length) {
      order = this.dataSourceOrder;
    }
    this.dataSources.forEach((ds) => {
      if (!order.includes(ds.key)) {
        order.push(ds.key);
      }
    });
    this.dataSources.sort((a, b) => order.indexOf(a.key) - order.indexOf(b.key));
  }

  public static registerDataSource(config: IDataSourceConfig<any>): void {
    this.dataSources.push(config);
    this.sortDataSources();
  }

  public static getDataSources(): Array<IDataSourceConfig<any>> {
    return cloneDeep(this.dataSources);
  }

  public static removeDataSource(key: string): void {
    this.dataSources = this.dataSources.filter((data) => {
      return data.key !== key;
    });
  }

  public static clearDataSources(): void {
    this.dataSources.length = 0;
  }
}
