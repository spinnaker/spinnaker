import { FilterModelService, IFilterConfig, IFilterModel } from '../../filterModel';

export const filterModelConfig: IFilterConfig[] = [
  { model: 'filter', param: 'q', clearValue: '', type: 'string', filterLabel: 'search' },
  { model: 'account', param: 'acct', type: 'trueKeyObject' },
  { model: 'region', param: 'reg', type: 'trueKeyObject' },
  { model: 'stack', param: 'stack', type: 'trueKeyObject' },
  { model: 'detail', param: 'detail', type: 'trueKeyObject' },
  { model: 'category', param: 'category', type: 'trueKeyObject' },
  {
    model: 'status',
    type: 'trueKeyObject',
    filterTranslator: { Up: 'Healthy', Down: 'Unhealthy', OutOfService: 'Out of Service' },
  },
  { model: 'availabilityZone', param: 'zone', type: 'trueKeyObject', filterLabel: 'availability zone' },
  { model: 'instanceType', type: 'trueKeyObject', filterLabel: 'instance type' },
  { model: 'providerType', type: 'trueKeyObject', filterLabel: 'provider' },
  { model: 'minInstances', type: 'int', filterLabel: 'instance count (min)' },
  { model: 'maxInstances', type: 'int', filterLabel: 'instance count (max)' },
  { model: 'showAllInstances', param: 'hideInstances', displayOption: true, type: 'inverse-boolean' },
  { model: 'listInstances', displayOption: true, type: 'boolean' },
  { model: 'instanceSort', displayOption: true, type: 'string', defaultValue: 'launchTime' },
  { model: 'multiselect', displayOption: true, type: 'boolean' },
  { model: 'clusters', type: 'trueKeyObject' },
  { model: 'labels', type: 'trueKeyObject', filterLabel: 'label', clearValue: {} },
];

export class ClusterFilterModel {
  public asFilterModel: IFilterModel;

  constructor() {
    this.asFilterModel = FilterModelService.configureFilterModel(this as any, filterModelConfig);
    FilterModelService.registerRouterHooks(this.asFilterModel, '**.application.insight.clusters.**');
    this.asFilterModel.activate();
  }
}
