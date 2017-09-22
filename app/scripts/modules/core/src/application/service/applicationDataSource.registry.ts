import { module } from 'angular';

import * as _ from 'lodash';

import { IDataSourceConfig } from './applicationDataSource';

export class ApplicationDataSourceRegistry {

  private defaultDataSourceOrder: string[] = ['executions', 'serverGroups', 'loadBalancers', 'securityGroups', 'tasks', 'config'];
  private dataSources: IDataSourceConfig[] = [];
  private dataSourceOrder: string[] = [];

  public setDataSourceOrder(keys: string[]): void {
    this.dataSourceOrder = keys;
    this.sortDataSources();
  }

  private sortDataSources(): void {
    let order = this.defaultDataSourceOrder;
    if (this.dataSourceOrder.length) {
      order = this.dataSourceOrder;
    }
    this.dataSources.forEach(ds => {
      if (!order.includes(ds.key)) {
        order.push(ds.key);
      }
    });
    this.dataSources.sort((a, b) => order.indexOf(a.key) - order.indexOf(b.key));
  }

  public registerDataSource(config: IDataSourceConfig): void {
    this.dataSources.push(config);
    this.sortDataSources();
  }

  public getDataSources(): IDataSourceConfig[] {
    return _.cloneDeep(this.dataSources);
  }
}

export const APPLICATION_DATA_SOURCE_REGISTRY = 'spinnaker.core.application.section.registry';

module(APPLICATION_DATA_SOURCE_REGISTRY, [])
  .service('applicationDataSourceRegistry', ApplicationDataSourceRegistry);
