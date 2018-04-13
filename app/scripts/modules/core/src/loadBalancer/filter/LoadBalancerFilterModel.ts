import { Ng1StateDeclaration, StateParams } from '@uirouter/angularjs';
import { $rootScope } from 'ngimport';

import { ILoadBalancerGroup } from 'core/domain';
import { IFilterConfig, IFilterModel } from 'core/filterModel/IFilterModel';
import { FilterModelService } from 'core/filterModel';
import { UrlParser } from 'core/navigation/urlParser';

export const filterModelConfig: IFilterConfig[] = [
  { model: 'account', param: 'acct', type: 'trueKeyObject' },
  { model: 'availabilityZone', param: 'zone', type: 'trueKeyObject', filterLabel: 'availability zone' },
  { model: 'detail', param: 'detail', type: 'trueKeyObject' },
  { model: 'filter', param: 'q', clearValue: '', type: 'string', filterLabel: 'search' },
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
  private mostRecentParams: any;
  public asFilterModel: ILoadBalancerFilterModel;

  constructor() {
    this.asFilterModel = FilterModelService.configureFilterModel(this as any, filterModelConfig);
    this.bindEvents();
    this.asFilterModel.activate();
  }

  private isLoadBalancerState(stateName: string) {
    return stateName === 'home.applications.application.insight.loadBalancers';
  }

  private isLoadBalancerStateOrChild(stateName: string) {
    return this.isLoadBalancerState(stateName) || this.isChildState(stateName);
  }

  private isChildState(stateName: string) {
    return stateName.includes('loadBalancers.');
  }

  private movingToLoadBalancerState(toState: Ng1StateDeclaration) {
    return this.isLoadBalancerStateOrChild(toState.name);
  }

  private movingFromLoadBalancerState(toState: Ng1StateDeclaration, fromState: Ng1StateDeclaration) {
    return this.isLoadBalancerStateOrChild(fromState.name) && !this.isLoadBalancerStateOrChild(toState.name);
  }

  private shouldRouteToSavedState(toParams: StateParams, fromState: Ng1StateDeclaration) {
    return this.asFilterModel.hasSavedState(toParams) && !this.isLoadBalancerStateOrChild(fromState.name);
  }

  private fromLoadBalancersState(fromState: Ng1StateDeclaration) {
    return (
      fromState.name.indexOf('home.applications.application.insight') === 0 &&
      !fromState.name.includes('home.applications.application.insight.loadBalancers')
    );
  }

  private bindEvents(): void {
    // WHY??? Because, when the stateChangeStart event fires, the $location.search() will return whatever the query
    // params are on the route we are going to, so if the user is using the back button, for example, to go to the
    // Infrastructure page with a search already entered, we'll pick up whatever search was entered there, and if we
    // come back to this application, we'll get whatever that search was.
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
        if (this.movingFromLoadBalancerState(toState, fromState)) {
          this.asFilterModel.saveState(fromState, fromParams, this.mostRecentParams);
        }
      },
    );

    $rootScope.$on(
      '$stateChangeSuccess',
      (_event, toState: Ng1StateDeclaration, toParams: StateParams, fromState: Ng1StateDeclaration) => {
        if (this.isLoadBalancerStateOrChild(toState.name) && this.isLoadBalancerStateOrChild(fromState.name)) {
          this.asFilterModel.applyParamsToUrl();
          return;
        }
        if (this.movingToLoadBalancerState(toState)) {
          if (this.shouldRouteToSavedState(toParams, fromState)) {
            this.asFilterModel.restoreState(toParams);
          }

          if (this.fromLoadBalancersState(fromState) && !this.asFilterModel.hasSavedState(toParams)) {
            this.asFilterModel.clearFilters();
          }
        }
      },
    );
  }
}
