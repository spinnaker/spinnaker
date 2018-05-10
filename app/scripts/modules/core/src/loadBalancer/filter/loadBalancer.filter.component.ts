import { chain, cloneDeep, compact, uniq, map } from 'lodash';
import { IScope, module } from 'angular';
import { $rootScope } from 'ngimport';
import { Subscription } from 'rxjs';

import { Application } from 'core/application/application.model';
import { LoadBalancerState } from 'core/state';

import { IFilterTag } from '../../filterModel/FilterTags';
import { digestDependentFilters } from '../../filterModel/dependentFilter/DependentFilterService';

export const LOAD_BALANCER_FILTER = 'spinnaker.core.loadBalancer.filter.controller';

const ngmodule = module('spinnaker.core.loadBalancer.filter.controller', []);

const poolValueCoordinates = [
  { filterField: 'providerType', on: 'loadBalancer', localField: 'type' },
  { filterField: 'account', on: 'loadBalancer', localField: 'account' },
  { filterField: 'region', on: 'loadBalancer', localField: 'region' },
  { filterField: 'availabilityZone', on: 'instance', localField: 'zone' },
];

function poolBuilder(loadBalancers: any[]) {
  const pool = chain(loadBalancers)
    .map(lb => {
      const poolUnitTemplate = chain(poolValueCoordinates)
        .filter({ on: 'loadBalancer' })
        .reduce(
          (acc, coordinate) => {
            acc[coordinate.filterField] = lb[coordinate.localField];
            return acc;
          },
          {} as any,
        )
        .value();

      const poolUnits = chain(['instances', 'detachedInstances'])
        .map(instanceStatus => lb[instanceStatus])
        .flatten<any>()
        .map(instance => {
          const poolUnit = cloneDeep(poolUnitTemplate);
          if (!instance) {
            return poolUnit;
          }

          return chain(poolValueCoordinates)
            .filter({ on: 'instance' })
            .reduce((acc, coordinate) => {
              acc[coordinate.filterField] = instance[coordinate.localField];
              return acc;
            }, poolUnit)
            .value();
        })
        .value();

      if (!poolUnits.length) {
        poolUnits.push(poolUnitTemplate);
      }

      return poolUnits;
    })
    .flatten()
    .value();

  return pool;
}

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

  constructor(private $scope: IScope) {
    'ngInject';
    this.sortFilter = LoadBalancerState.filterModel.asFilterModel.sortFilter;
  }

  public $onInit(): void {
    const { app, $scope } = this;
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
    const { app } = this;

    const { availabilityZone, region, account } = digestDependentFilters({
      sortFilter: LoadBalancerState.filterModel.asFilterModel.sortFilter,
      dependencyOrder: ['providerType', 'account', 'region', 'availabilityZone'],
      pool: poolBuilder(app.loadBalancers.data),
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
