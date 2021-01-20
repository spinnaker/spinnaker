import { ILoadBalancerGroup } from 'core/domain';
import { IFilterConfig, IFilterModel } from 'core/filterModel/IFilterModel';
import { FilterModelService } from 'core/filterModel';

export const filterModelConfig: IFilterConfig[] = [
  { model: 'account', param: 'acct', type: 'trueKeyObject' },
  { model: 'availabilityZone', param: 'zone', type: 'trueKeyObject', filterLabel: 'availability zone' },
  { model: 'detail', param: 'detail', type: 'trueKeyObject' },
  { model: 'filter', param: 'q', clearValue: '', type: 'string', filterLabel: 'search' },
  { model: 'loadBalancerType', param: 'loadBalancerType', filterLabel: 'type', type: 'trueKeyObject' },
  { model: 'providerType', type: 'trueKeyObject', filterLabel: 'provider' },
  { model: 'region', param: 'reg', type: 'trueKeyObject' },
  { model: 'showInstances', displayOption: true, type: 'boolean' },
  { model: 'showServerGroups', param: 'hideServerGroups', displayOption: true, type: 'inverse-boolean' },
  { model: 'stack', param: 'stack', type: 'trueKeyObject' },
  {
    model: 'status',
    type: 'trueKeyObject',
    filterTranslator: { Up: 'Healthy', Down: 'Unhealthy', OutOfService: 'Out of Service' },
  },
];

export interface ILoadBalancerFilterModel extends IFilterModel {
  groups: ILoadBalancerGroup[];
}

export class LoadBalancerFilterModel {
  public asFilterModel: ILoadBalancerFilterModel;

  constructor() {
    this.asFilterModel = FilterModelService.configureFilterModel(this as any, filterModelConfig);
    FilterModelService.registerRouterHooks(this.asFilterModel, '**.application.insight.loadBalancers.**');
    this.asFilterModel.activate();
  }
}
