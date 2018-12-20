import { Ng1StateDeclaration, StateParams } from '@uirouter/angularjs';
import { $rootScope } from 'ngimport';

import { FilterModelService, IFilterConfig, IFilterModel } from 'core/filterModel';
import { UrlParser } from 'core/navigation/urlParser';

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
  private mostRecentParams: any;
  public asFilterModel: IFilterModel;

  constructor() {
    this.asFilterModel = FilterModelService.configureFilterModel(this as any, filterModelConfig);
    this.bindEvents();
    this.asFilterModel.activate();
  }

  private isClusterState(stateName: string): boolean {
    return (
      stateName === 'home.applications.application.insight.clusters' ||
      stateName === 'home.project.application.insight.clusters'
    );
  }

  private isClusterStateOrChild(stateName: string): boolean {
    return this.isClusterState(stateName) || this.isChildState(stateName);
  }

  private isChildState(stateName: string): boolean {
    return stateName.includes('clusters.');
  }

  private movingToClusterState(toState: Ng1StateDeclaration): boolean {
    return this.isClusterStateOrChild(toState.name);
  }

  private movingFromClusterState(toState: Ng1StateDeclaration, fromState: Ng1StateDeclaration): boolean {
    return this.isClusterStateOrChild(fromState.name) && !this.isClusterStateOrChild(toState.name);
  }

  private fromApplicationListState(fromState: Ng1StateDeclaration): boolean {
    return fromState.name === 'home.applications';
  }

  private shouldRouteToSavedState(toParams: StateParams, fromState: Ng1StateDeclaration): boolean {
    return this.asFilterModel.hasSavedState(toParams) && !this.isClusterStateOrChild(fromState.name);
  }

  private bindEvents(): void {
    // WHY??? Because, when the stateChangeStart event fires, the $location.search() will return whatever the query
    // params are on the route we are going to, so if the user is using the back button, for example, to go to the
    // Infrastructure page with a search already entered, we'll pick up whatever search was entered there, and if we
    // come back to this application's clusters view, we'll get whatever that search was.
    $rootScope.$on('$locationChangeStart', (_event, toUrl: string, fromUrl: string) => {
      const [oldBase, oldQuery] = fromUrl.split('?'),
        [newBase, newQuery] = toUrl.split('?');

      if (oldBase === newBase) {
        this.mostRecentParams = newQuery ? UrlParser.parseQueryString(newQuery) : {};
      } else {
        this.mostRecentParams = oldQuery ? UrlParser.parseQueryString(oldQuery) : {};
      }
    });

    $rootScope.$on(
      '$stateChangeStart',
      (
        _event,
        toState: Ng1StateDeclaration,
        _toParams: StateParams,
        fromState: Ng1StateDeclaration,
        fromParams: StateParams,
      ) => {
        if (this.movingFromClusterState(toState, fromState)) {
          this.asFilterModel.saveState(fromState, fromParams, this.mostRecentParams);
        }
      },
    );

    $rootScope.$on(
      '$stateChangeSuccess',
      (_event, toState: Ng1StateDeclaration, toParams: StateParams, fromState: Ng1StateDeclaration) => {
        if (this.movingToClusterState(toState) && this.isClusterStateOrChild(fromState.name)) {
          this.asFilterModel.applyParamsToUrl();
          return;
        }
        if (this.movingToClusterState(toState)) {
          if (this.shouldRouteToSavedState(toParams, fromState)) {
            this.asFilterModel.restoreState(toParams);
          }
          if (this.fromApplicationListState(fromState) && !this.asFilterModel.hasSavedState(toParams)) {
            this.asFilterModel.clearFilters();
          }
        }
      },
    );
  }
}
