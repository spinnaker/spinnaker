import { compact, uniq, map } from 'lodash';
import { IScope, module } from 'angular';
import { Subscription } from 'rxjs';

import { Application } from 'core/application/application.model';
import { LoadBalancerState } from 'core/state';

import { IFilterTag } from '../../filterModel/FilterTags';

export const LOAD_BALANCER_FILTER = 'spinnaker.core.loadBalancer.filter.controller';

const ngmodule = module('spinnaker.core.loadBalancer.filter.controller', [
  require('../../filterModel/dependentFilter/dependentFilter.service').name,
  require('./loadBalancerDependentFilterHelper.service').name,
]);

class LoadBalancerFilterCtrl {
  public accountHeadings: string[];
  public app: Application;
  public availabilityZoneHeadings: string[];
  public providerTypeHeadings: string[];
  public regionHeadings: string[];
  public sortFilter: any;
  public stackHeadings: string[];
  public detailHeadings: string[];
  public tags: IFilterTag[];
  private groupsUpdatedSubscription: Subscription;
  private locationChangeUnsubscribe: () => void;

  constructor(
    private $scope: IScope,
    private $rootScope: IScope,
    private loadBalancerDependentFilterHelper: any,
    private dependentFilterService: any,
  ) {
    'ngInject';
    this.sortFilter = LoadBalancerState.filterModel.asFilterModel.sortFilter;
  }

  public $onInit(): void {
    const { app, $scope, $rootScope } = this;
    const filterModel = LoadBalancerState.filterModel.asFilterModel;

    this.tags = filterModel.tags;
    this.groupsUpdatedSubscription = LoadBalancerState.filterService.groupsUpdatedStream.subscribe(() => {
      // need to applyAsync because everything else is happening in React now; will replicate when converting clusters, etc.
      $scope.$applyAsync(() => (this.tags = filterModel.tags));
    });

    if (app.loadBalancers && app.loadBalancers.loaded) {
      this.initialize();
    }

    app.loadBalancers.onRefresh($scope, () => this.initialize());

    this.locationChangeUnsubscribe = $rootScope.$on('$locationChangeSuccess', () => {
      filterModel.activate();
      LoadBalancerState.filterService.updateLoadBalancerGroups(app);
    });

    $scope.$on('$destroy', () => {
      this.groupsUpdatedSubscription.unsubscribe();
      this.locationChangeUnsubscribe();
    });
  }

  public updateLoadBalancerGroups(applyParamsToUrl = true): void {
    const { dependentFilterService, loadBalancerDependentFilterHelper, app } = this;

    const { availabilityZone, region, account } = dependentFilterService.digestDependentFilters({
      sortFilter: LoadBalancerState.filterModel.asFilterModel.sortFilter,
      dependencyOrder: ['providerType', 'account', 'region', 'availabilityZone'],
      pool: loadBalancerDependentFilterHelper.poolBuilder(app.loadBalancers.data),
    });

    this.accountHeadings = account;
    this.regionHeadings = region;
    this.availabilityZoneHeadings = availabilityZone;

    if (applyParamsToUrl) {
      LoadBalancerState.filterModel.asFilterModel.applyParamsToUrl();
    }
    LoadBalancerState.filterService.updateLoadBalancerGroups(app);
  }

  private getHeadingsForOption(option: string): string[] {
    return compact(uniq(map(this.app.loadBalancers.data, option) as string[])).sort();
  }

  public clearFilters(): void {
    const { app } = this;

    LoadBalancerState.filterService.clearFilters();
    LoadBalancerState.filterService.updateLoadBalancerGroups(app);
    this.updateLoadBalancerGroups(false);
  }

  public initialize(): void {
    this.stackHeadings = ['(none)'].concat(this.getHeadingsForOption('stack'));
    this.detailHeadings = ['(none)'].concat(this.getHeadingsForOption('detail'));
    this.providerTypeHeadings = this.getHeadingsForOption('type');
    this.updateLoadBalancerGroups();
  }
}

ngmodule.component('loadBalancerFilter', {
  templateUrl: require('./loadBalancer.filter.component.html'),
  controller: LoadBalancerFilterCtrl,
  bindings: {
    app: '<',
  },
});
