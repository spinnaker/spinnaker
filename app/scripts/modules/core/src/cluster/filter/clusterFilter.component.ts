import { IScope, module } from 'angular';
import { compact, uniq, map } from 'lodash';
import { Subscription } from 'rxjs';

import { Application } from 'core/application/application.model';
import { ClusterState } from 'core/state';
import { IFilterTag, ISortFilter } from 'core/filterModel';

export const CLUSTER_FILTER = 'spinnaker.core.cluster.filter.component';

const ngmodule = module(CLUSTER_FILTER, [
  require('./collapsibleFilterSection.directive').name,
  require('core/filterModel/dependentFilter/dependentFilter.service').name,
  require('./clusterDependentFilterHelper.service').name,
]);

class ClusterFilterCtrl {
  public accountHeadings: string[];
  public app: Application;
  public availabilityZoneHeadings: string[];
  public categoryHeadings: string[];
  public instanceTypeHeadings: string[];
  public providerTypeHeadings: string[];
  public regionHeadings: string[];
  public sortFilter: ISortFilter;
  public stackHeadings: string[];
  public detailHeadings: string[];
  public tags: IFilterTag[];
  private groupsUpdatedSubscription: Subscription;
  private locationChangeUnsubscribe: () => void;

  constructor(
    public $scope: IScope,
    public $rootScope: IScope,
    public clusterDependentFilterHelper: any,
    public dependentFilterService: any,
  ) {
    'ngInject';
  }

  public $onInit(): void {
    const { $scope, $rootScope, app } = this;
    const filterModel = ClusterState.filterModel.asFilterModel;

    this.sortFilter = filterModel.sortFilter;
    this.tags = filterModel.tags;
    this.groupsUpdatedSubscription = ClusterState.filterService.groupsUpdatedStream.subscribe(
      () => (this.tags = filterModel.tags),
    );

    if (app.serverGroups.loaded) {
      this.initialize();
    }

    app.serverGroups.onRefresh($scope, () => this.initialize());
    this.locationChangeUnsubscribe = $rootScope.$on('$locationChangeSuccess', () => {
      filterModel.activate();
      ClusterState.filterService.updateClusterGroups(app);
    });

    $scope.$on('$destroy', () => {
      this.groupsUpdatedSubscription.unsubscribe();
      this.locationChangeUnsubscribe();
    });
  }

  public updateClusterGroups(applyParamsToUrl = true): void {
    const { dependentFilterService, clusterDependentFilterHelper, app } = this;

    const {
      providerType,
      instanceType,
      account,
      availabilityZone,
      region,
    } = dependentFilterService.digestDependentFilters({
      sortFilter: ClusterState.filterModel.asFilterModel.sortFilter,
      dependencyOrder: ['providerType', 'account', 'region', 'availabilityZone', 'instanceType'],
      pool: clusterDependentFilterHelper.poolBuilder(app.serverGroups.data),
    });

    this.providerTypeHeadings = providerType;
    this.accountHeadings = account;
    this.availabilityZoneHeadings = availabilityZone;
    this.regionHeadings = region;
    this.instanceTypeHeadings = instanceType;
    if (applyParamsToUrl) {
      ClusterState.filterModel.asFilterModel.applyParamsToUrl();
    }
    ClusterState.filterService.updateClusterGroups(app);
  }

  private getHeadingsForOption(option: string): string[] {
    return compact(uniq(map(this.app.serverGroups.data, option) as string[])).sort();
  }

  public clearFilters(): void {
    const { app } = this;
    ClusterState.filterService.clearFilters();
    ClusterState.filterService.updateClusterGroups(app);
    this.updateClusterGroups(false);
  }

  public initialize(): void {
    this.stackHeadings = ['(none)'].concat(this.getHeadingsForOption('stack'));
    this.detailHeadings = ['(none)'].concat(this.getHeadingsForOption('detail'));
    this.categoryHeadings = this.getHeadingsForOption('category');
    this.updateClusterGroups();
  }
}

ngmodule.component('clusterFilter', {
  templateUrl: require('./clusterFilter.component.html'),
  controller: ClusterFilterCtrl,
  bindings: {
    app: '<',
  },
});
