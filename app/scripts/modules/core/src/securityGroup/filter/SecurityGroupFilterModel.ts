import { IAngularEvent, module } from 'angular';
import { Ng1StateDeclaration, StateParams } from '@uirouter/angularjs';
import { $rootScope } from 'ngimport';

import { IFilterConfig, IFilterModel } from 'core/filterModel/IFilterModel';
import { UrlParser } from 'core/navigation/urlParser';
import { FilterModelService } from 'core/filterModel';

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
  private mostRecentParams: any;
  public asFilterModel: IFilterModel;

  constructor() {
    this.asFilterModel = FilterModelService.configureFilterModel(this as any, filterModelConfig);
    this.bindEvents();
    this.asFilterModel.activate();
  }

  private isSecurityGroupState(stateName: string) {
    return stateName === 'home.applications.application.insight.securityGroups';
  }

  private isSecurityGroupStateOrChild(stateName: string) {
    return this.isSecurityGroupState(stateName) || this.isChildState(stateName);
  }

  private isChildState(stateName: string) {
    return stateName.includes('securityGroups.');
  }

  private movingToSecurityGroupState(toState: Ng1StateDeclaration) {
    return this.isSecurityGroupStateOrChild(toState.name);
  }

  private movingFromSecurityGroupState(toState: Ng1StateDeclaration, fromState: Ng1StateDeclaration) {
    return this.isSecurityGroupStateOrChild(fromState.name) && !this.isSecurityGroupStateOrChild(toState.name);
  }

  private shouldRouteToSavedState(toParams: StateParams, fromState: Ng1StateDeclaration) {
    return this.asFilterModel.hasSavedState(toParams) && !this.isSecurityGroupStateOrChild(fromState.name);
  }

  private fromSecurityGroupsState(fromState: Ng1StateDeclaration) {
    return (
      fromState.name.indexOf('home.applications.application.insight') === 0 &&
      !fromState.name.includes('home.applications.application.insight.securityGroups')
    );
  }

  private bindEvents(): void {
    // WHY??? Because, when the stateChangeStart event fires, the $location.search() will return whatever the query
    // params are on the route we are going to, so if the user is using the back button, for example, to go to the
    // Infrastructure page with a search already entered, we'll pick up whatever search was entered there, and if we
    // come back to this application, we'll get whatever that search was.
    $rootScope.$on('$locationChangeStart', (_event: IAngularEvent, toUrl: string, fromUrl: string) => {
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
        _event: IAngularEvent,
        toState: Ng1StateDeclaration,
        _toParams: StateParams,
        fromState: Ng1StateDeclaration,
        fromParams: StateParams,
      ) => {
        if (this.movingFromSecurityGroupState(toState, fromState)) {
          this.asFilterModel.saveState(fromState, fromParams, this.mostRecentParams);
        }
      },
    );

    $rootScope.$on(
      '$stateChangeSuccess',
      (_event: IAngularEvent, toState: Ng1StateDeclaration, toParams: StateParams, fromState: Ng1StateDeclaration) => {
        if (this.isSecurityGroupStateOrChild(toState.name) && this.isSecurityGroupStateOrChild(fromState.name)) {
          this.asFilterModel.applyParamsToUrl();
          return;
        }
        if (this.movingToSecurityGroupState(toState)) {
          if (this.shouldRouteToSavedState(toParams, fromState)) {
            this.asFilterModel.restoreState(toParams);
          }

          if (this.fromSecurityGroupsState(fromState) && !this.asFilterModel.hasSavedState(toParams)) {
            this.asFilterModel.clearFilters();
          }
        }
      },
    );
  }
}

module(SECURITY_GROUP_FILTER_MODEL, []).service('securityGroupFilterModel', SecurityGroupFilterModel);
