import {DataSourceConfig} from './applicationDataSource.ts'
import {module} from 'angular';
import LoDashStatic = _.LoDashStatic;

export class ApplicationDataSourceRegistry {

  private defaultDataSourceOrder: string[] = ['executions', 'serverGroups', 'loadBalancers', 'securityGroups', 'tasks', 'config'];
  static get $inject() { return ['_']; }

  constructor(private _: LoDashStatic) {}

  private dataSources: DataSourceConfig[] = [];
  private dataSourceOrder: string[] = [];

  setDataSourceOrder(keys: string[]): void {
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

  registerDataSource(config: any): void {
    this.dataSources.push(new DataSourceConfig(config));
    this.sortDataSources();
  }

  getDataSources(): DataSourceConfig[] {
    return this._.cloneDeep(this.dataSources);
  }
}

const moduleName = 'spinnaker.core.application.section.registry';

module(moduleName, [
  require('../../utils/lodash.js')
  ])
  .service('applicationDataSourceRegistry', ApplicationDataSourceRegistry);

export default moduleName;
