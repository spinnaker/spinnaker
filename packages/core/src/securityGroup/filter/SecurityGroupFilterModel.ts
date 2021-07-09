import { module } from 'angular';
import { FilterModelService } from '../../filterModel';
import { IFilterConfig, IFilterModel } from '../../filterModel/IFilterModel';

export const SECURITY_GROUP_FILTER_MODEL = 'spinnaker.core.securityGroup.filter.model';
export const filterModelConfig: IFilterConfig[] = [
  { model: 'account', param: 'acct', type: 'trueKeyObject' },
  { model: 'detail', param: 'detail', type: 'trueKeyObject' },
  { model: 'filter', param: 'q', clearValue: '', type: 'string', filterLabel: 'search' },
  { model: 'providerType', type: 'trueKeyObject', filterLabel: 'provider' },
  { model: 'region', param: 'reg', type: 'trueKeyObject' },
  { model: 'showLoadBalancers', param: 'hideLoadBalancers', displayOption: true, type: 'inverse-boolean' },
  { model: 'showServerGroups', param: 'hideServerGroups', displayOption: true, type: 'inverse-boolean' },
  { model: 'stack', param: 'stack', type: 'trueKeyObject' },
];

export class SecurityGroupFilterModel {
  public asFilterModel: IFilterModel;

  constructor() {
    this.asFilterModel = FilterModelService.configureFilterModel(this as any, filterModelConfig);
    FilterModelService.registerRouterHooks(this.asFilterModel, '**.application.insight.firewalls.**');
    this.asFilterModel.activate();
  }
}

module(SECURITY_GROUP_FILTER_MODEL, []).service('securityGroupFilterModel', SecurityGroupFilterModel);
