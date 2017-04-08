import {compact, uniq, map} from 'lodash';
import {IScope, module} from 'angular';
import {Application} from '../../application/application.model';

export const LOAD_BALANCER_FILTER = 'spinnaker.core.loadBalancer.filter.controller';

let ngmodule = module('spinnaker.core.loadBalancer.filter.controller', [
  require('./loadBalancer.filter.service'),
  require('./loadBalancer.filter.model'),
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

  static get $inject() {
    return [
      '$scope',
      'loadBalancerFilterService',
      'LoadBalancerFilterModel',
      '$rootScope',
      'loadBalancerDependentFilterHelper',
      'dependentFilterService',
    ];
  }

  constructor(private $scope: IScope,
              private loadBalancerFilterService: any,
              private LoadBalancerFilterModel: any,
              private $rootScope: IScope,
              private loadBalancerDependentFilterHelper: any,
              private dependentFilterService: any,
  ) {
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

  public updateLoadBalancerGroups(): void {
    const { dependentFilterService, LoadBalancerFilterModel, loadBalancerFilterService, loadBalancerDependentFilterHelper, app } = this;

    const { availabilityZone, region, account } = dependentFilterService.digestDependentFilters({
      sortFilter: LoadBalancerFilterModel.sortFilter,
      dependencyOrder: ['providerType', 'account', 'region', 'availabilityZone'],
      pool: loadBalancerDependentFilterHelper.poolBuilder(app.loadBalancers.data)
    });

    this.accountHeadings = account;
    this.regionHeadings = region;
    this.availabilityZoneHeadings = availabilityZone;

    LoadBalancerFilterModel.applyParamsToUrl();
    loadBalancerFilterService.updateLoadBalancerGroups(app);
  }

  private getHeadingsForOption(option: string): string[] {
    return compact(uniq(map(this.app.loadBalancers.data, option) as string[])).sort();
  }

  public clearFilters(): void {
    const { loadBalancerFilterService, app } = this;

    loadBalancerFilterService.clearFilters();
    loadBalancerFilterService.updateLoadBalancerGroups(app);
    this.updateLoadBalancerGroups();
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

