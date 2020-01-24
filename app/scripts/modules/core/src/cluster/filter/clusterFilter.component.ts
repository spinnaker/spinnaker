import { IScope, ITimeoutService, module } from 'angular';
import { compact, uniq, map } from 'lodash';
import { Subscription } from 'rxjs';

import { Application } from 'core/application/application.model';
import { ClusterState } from 'core/state';
import { IFilterTag, ISortFilter, digestDependentFilters } from 'core/filterModel';
import {
  buildLabelsMap,
  labelFiltersToTrueKeyObject,
  trueKeyObjectToLabelFilters,
  ILabelFilter,
  ILabelsMap,
} from './labelFilterUtils';
import { CORE_CLUSTER_FILTER_COLLAPSIBLEFILTERSECTION_DIRECTIVE } from './collapsibleFilterSection.directive';
import { CORE_CLUSTER_FILTER_CLUSTERDEPENDENTFILTERHELPER_SERVICE } from './clusterDependentFilterHelper.service';

export const CLUSTER_FILTER = 'spinnaker.core.cluster.filter.component';

const ngmodule = module(CLUSTER_FILTER, [
  CORE_CLUSTER_FILTER_COLLAPSIBLEFILTERSECTION_DIRECTIVE,
  CORE_CLUSTER_FILTER_CLUSTERDEPENDENTFILTERHELPER_SERVICE,
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
  public labelsMap: ILabelsMap;
  public labelFilters: ILabelFilter[];
  public showLabelFilter: boolean;
  private groupsUpdatedSubscription: Subscription;
  private locationChangeUnsubscribe: () => void;

  public static $inject = ['$scope', '$rootScope', '$timeout', 'clusterDependentFilterHelper'];
  constructor(
    public $scope: IScope,
    public $rootScope: IScope,
    public $timeout: ITimeoutService,
    public clusterDependentFilterHelper: any,
  ) {}

  public $onInit(): void {
    const { $scope, $rootScope, $timeout, app } = this;
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
      $timeout(() => {
        filterModel.activate();
        ClusterState.filterService.updateClusterGroups(app);
      });
    });

    $scope.$on('$destroy', () => {
      this.groupsUpdatedSubscription.unsubscribe();
      this.locationChangeUnsubscribe();
    });

    this.labelsMap = buildLabelsMap(this.app.getDataSource('serverGroups').data);
    this.showLabelFilter = Object.keys(this.labelsMap).length > 0;

    if (this.showLabelFilter) {
      this.labelFilters = trueKeyObjectToLabelFilters(this.sortFilter.labels);
      $scope.sortFilter = this.sortFilter;
      $scope.$watch('sortFilter.labels', this.syncLabelFilters);
    }
  }

  public updateClusterGroups(applyParamsToUrl = true): void {
    const { clusterDependentFilterHelper, app } = this;

    const { providerType, instanceType, account, availabilityZone, region } = digestDependentFilters({
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

  public onLabelFiltersChange = (labelFilters: ILabelFilter[]): void => {
    // Called from LabelFilter React component
    this.$scope.$applyAsync(() => {
      this.labelFilters = labelFilters;
      this.sortFilter.labels = labelFiltersToTrueKeyObject(labelFilters);
      this.updateClusterGroups();
    });
  };

  private syncLabelFilters = (labelFilters: { [key: string]: boolean }) => {
    /**
     * When state changes originate in LabelFilter React component, this.onLabelFiltersChange
     * keeps React props in sync with Angular model. However, labels can be
     * cleared externally, and need to be reset from URL when toggling between
     * infrastructure views.
     */
    const currentKeys = this.labelFilters.map(f => f.key);
    const nextKeys = trueKeyObjectToLabelFilters(labelFilters).map(f => f.key);
    if (currentKeys.length > nextKeys.length) {
      // One or more labels have been removed -- order is preserved
      this.labelFilters = this.labelFilters.filter(f => nextKeys.includes(f.key));
    } else if (currentKeys.length === 0 && nextKeys.length > 0) {
      // Resetting after page change -- order may not be preserved
      this.labelFilters = trueKeyObjectToLabelFilters(labelFilters);
    }
  };
}

ngmodule.component('clusterFilter', {
  templateUrl: require('./clusterFilter.component.html'),
  controller: ClusterFilterCtrl,
  bindings: {
    app: '<',
  },
});
