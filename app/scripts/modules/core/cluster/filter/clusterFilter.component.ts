import {compact, uniq, map} from 'lodash';
import {IScope, module} from 'angular';

import {CLUSTER_FILTER_SERVICE, ClusterFilterService} from 'core/cluster/filter/clusterFilter.service';
import {Application} from '../../application/application.model';

export const CLUSTER_FILTER = 'spinnaker.core.cluster.filter.component';

const ngmodule = module(CLUSTER_FILTER, [
  require('./collapsibleFilterSection.directive'),
  CLUSTER_FILTER_SERVICE,
  require('./clusterFilter.model'),
  require('../../filterModel/dependentFilter/dependentFilter.service'),
  require('./clusterDependentFilterHelper.service'),
]);

class ClusterFilterCtrl {
  public accountHeadings: string[];
  public app: Application;
  public availabilityZoneHeadings: string[];
  public categoryHeadings: string[];
  public instanceTypeHeadings: string[];
  public providerTypeHeadings: string[];
  public regionHeadings: string[];
  public sortFilter: any;
  public stackHeadings: string[];
  public tags: any[];

  static get $inject() {
    return [
      '$scope',
      'clusterFilterService',
      'ClusterFilterModel',
      '$rootScope',
      'clusterDependentFilterHelper',
      'dependentFilterService',
    ];
  }

  constructor(public $scope: IScope,
              public clusterFilterService: ClusterFilterService,
              public ClusterFilterModel: any,
              public $rootScope: IScope,
              public clusterDependentFilterHelper: any,
              public dependentFilterService: any,
  ) {
  }

  public $onInit(): void {
    const { $scope, $rootScope, ClusterFilterModel, clusterFilterService, app } = this;

    this.sortFilter = ClusterFilterModel.sortFilter;
    this.tags = ClusterFilterModel.tags;

    if (app.serverGroups.loaded) {
      this.initialize();
    }

    app.serverGroups.onRefresh($scope, () => this.initialize());

    $scope.$on('$destroy', $rootScope.$on('$locationChangeSuccess', () => {
      ClusterFilterModel.activate();
      clusterFilterService.updateClusterGroups(app);
    }));
  }

  public updateClusterGroups(): void {
    const { dependentFilterService, ClusterFilterModel, clusterDependentFilterHelper, clusterFilterService, app } = this;

    const { providerType, instanceType, account, availabilityZone, region } = dependentFilterService.digestDependentFilters({
      sortFilter: ClusterFilterModel.sortFilter,
      dependencyOrder: ['providerType', 'account', 'region', 'availabilityZone', 'instanceType'],
      pool: clusterDependentFilterHelper.poolBuilder(app.serverGroups.data)
    });

    this.providerTypeHeadings = providerType;
    this.accountHeadings = account;
    this.availabilityZoneHeadings = availabilityZone;
    this.regionHeadings = region;
    this.instanceTypeHeadings = instanceType;

    ClusterFilterModel.applyParamsToUrl();
    clusterFilterService.updateClusterGroups(app);
  }

  private getHeadingsForOption(option: string): string[] {
    return compact(uniq(map(this.app.serverGroups.data, option) as string[])).sort();
  }

  public clearFilters(): void {
    const {clusterFilterService, app} = this;
    clusterFilterService.clearFilters();
    clusterFilterService.updateClusterGroups(app);
    this.updateClusterGroups();
  }

  public initialize(): void {
    this.stackHeadings = ['(none)'].concat(this.getHeadingsForOption('stack'));
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
