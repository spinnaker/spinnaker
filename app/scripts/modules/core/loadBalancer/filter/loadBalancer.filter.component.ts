import { compact, uniq, map } from 'lodash';
import { IScope, module } from 'angular';

import { Application } from 'core/application/application.model';
import { LOAD_BALANCER_FILTER_MODEL } from './loadBalancerFilter.model';
import { LOAD_BALANCER_FILTER_SERVICE } from './loadBalancer.filter.service';

export const LOAD_BALANCER_FILTER = 'spinnaker.core.loadBalancer.filter.controller';

const ngmodule = module('spinnaker.core.loadBalancer.filter.controller', [
  LOAD_BALANCER_FILTER_SERVICE,
  LOAD_BALANCER_FILTER_MODEL,
  require('../../filterModel/dependentFilter/dependentFilter.service'),
  require('./loadBalancerDependentFilterHelper.service'),
]);

class LoadBalancerFilterCtrl {
  public accountHeadings: string[];
  public app: Application;
  public availabilityZoneHeadings: string[];
  public providerTypeHeadings: string[];
  public regionHeadings: string[];
  public sortFilter: any;
  public stackHeadings: string[];
  public tags: any[];

  constructor(private $scope: IScope,
              private loadBalancerFilterService: any,
              private LoadBalancerFilterModel: any,
              private $rootScope: IScope,
              private loadBalancerDependentFilterHelper: any,
              private dependentFilterService: any,
  ) {
    'ngInject';
    this.sortFilter = LoadBalancerFilterModel.sortFilter;
  }

  public $onInit(): void {
    const { LoadBalancerFilterModel, loadBalancerFilterService, app, $scope, $rootScope } = this;

    this.tags = LoadBalancerFilterModel.tags;

    if (app.loadBalancers.loaded) {
      this.initialize();
    }

    app.loadBalancers.onRefresh($scope, () => this.initialize());

    $scope.$on('$destroy', $rootScope.$on('$locationChangeSuccess', () => {
      LoadBalancerFilterModel.activate();
      loadBalancerFilterService.updateLoadBalancerGroups(app);
    }));
  }

  public updateLoadBalancerGroups(applyParamsToUrl = true): void {
    const { dependentFilterService, LoadBalancerFilterModel, loadBalancerFilterService, loadBalancerDependentFilterHelper, app } = this;

    const { availabilityZone, region, account } = dependentFilterService.digestDependentFilters({
      sortFilter: LoadBalancerFilterModel.sortFilter,
      dependencyOrder: ['providerType', 'account', 'region', 'availabilityZone'],
      pool: loadBalancerDependentFilterHelper.poolBuilder(app.loadBalancers.data)
    });

    this.accountHeadings = account;
    this.regionHeadings = region;
    this.availabilityZoneHeadings = availabilityZone;

    if (applyParamsToUrl) {
      LoadBalancerFilterModel.applyParamsToUrl();
    }
    loadBalancerFilterService.updateLoadBalancerGroups(app);
  }

  private getHeadingsForOption(option: string): string[] {
    return compact(uniq(map(this.app.loadBalancers.data, option) as string[])).sort();
  }

  public clearFilters(): void {
    const { loadBalancerFilterService, app } = this;

    loadBalancerFilterService.clearFilters();
    loadBalancerFilterService.updateLoadBalancerGroups(app);
    this.updateLoadBalancerGroups(false);
  }

  public initialize(): void {
    this.stackHeadings = ['(none)'].concat(this.getHeadingsForOption('stack'));
    this.providerTypeHeadings = this.getHeadingsForOption('type');
    this.updateLoadBalancerGroups();
  }
}

ngmodule.component('loadBalancerFilter', {
  templateUrl: require('./loadBalancer.filter.component.html'),
  controller: LoadBalancerFilterCtrl,
  bindings: {
    app: '<'
  }
});

