import {IAngularEvent, IRootScopeService, module} from 'angular';
import {Ng1StateDeclaration, StateParams} from 'angular-ui-router';
import {extend} from 'lodash';

import {ICache} from 'core/cache/deckCache.service';
import {IExecutionGroup} from 'core/domain';
import {VIEW_STATE_CACHE_SERVICE, ViewStateCacheService} from 'core/cache/viewStateCache.service';
import {Subject} from 'rxjs/Subject';
import { IFilterConfig, IFilterModel } from 'core/filterModel/IFilterModel';

export const filterModelConfig: IFilterConfig[] = [
  { model: 'filter', param: 'q', clearValue: '', type: 'string', filterLabel: 'search', },
  { model: 'pipeline', param: 'pipeline', type: 'trueKeyObject', },
  { model: 'status', type: 'trueKeyObject', },
];

interface IExecutionFilterModel extends IFilterModel {
  groups: IExecutionGroup[];
}
export class ExecutionFilterModel {
  // Store count globally for 180 days
  private configViewStateCache: ICache;

  private groupCount: number;
  private groupBy: string;
  private showStageDuration: boolean;

  public asFilterModel: IExecutionFilterModel;

  // This is definitely not the best way to do this, but already have a Subject in here, so just using the same
  // mechanism for now.
  public expandSubject: Subject<boolean> = new Subject<boolean>();

  constructor($rootScope: IRootScopeService,
              filterModelService: any,
              urlParser: any,
              viewStateCache: ViewStateCacheService) {
    'ngInject';
    this.configViewStateCache = viewStateCache.createCache('executionFilters', {
      version: 1,
      maxAge: 180 * 24 * 60 * 60 * 1000,
    });
    this.groupCount = this.getCachedViewState().count;
    this.groupBy = this.getCachedViewState().groupBy;
    this.showStageDuration = this.getCachedViewState().showStageDuration;

    this.asFilterModel = filterModelService.configureFilterModel(this, filterModelConfig);

    let mostRecentParams: string = null;
    // WHY??? Because, when the stateChangeStart event fires, the $location.search() will return whatever the query
    // params are on the route we are going to, so if the user is using the back button, for example, to go to the
    // Infrastructure page with a search already entered, we'll pick up whatever search was entered there, and if we
    // come back to this application's clusters view, we'll get whatever that search was.
    $rootScope.$on('$locationChangeStart', (_event: IAngularEvent, toUrl: string, fromUrl: string) => {
      const [oldBase, oldQuery] = fromUrl.split('?'),
            [newBase, newQuery] = toUrl.split('?');

      if (oldBase === newBase) {
        mostRecentParams = newQuery ? urlParser.parseQueryString(newQuery) : {};
      } else {
        mostRecentParams = oldQuery ? urlParser.parseQueryString(oldQuery) : {};
      }
    });

    $rootScope.$on('$stateChangeStart', (_event: IAngularEvent, toState: Ng1StateDeclaration, _toParams: StateParams, fromState: Ng1StateDeclaration, fromParams: StateParams) => {
      if (this.movingFromExecutionsState(toState, fromState)) {
        this.asFilterModel.saveState(fromState, fromParams, mostRecentParams);
      }
    });

    $rootScope.$on('$stateChangeSuccess', (_event: IAngularEvent, toState: Ng1StateDeclaration, toParams: StateParams, fromState: Ng1StateDeclaration) => {
      if (this.movingToExecutionsState(toState) && this.isExecutionStateOrChild(fromState.name)) {
        this.asFilterModel.applyParamsToUrl();
        return;
      }
      if (this.movingToExecutionsState(toState)) {
        if (this.shouldRouteToSavedState(toParams, fromState)) {
          this.asFilterModel.restoreState(toParams);
        }
        if (this.fromApplicationListState(fromState) && !this.asFilterModel.hasSavedState(toParams)) {
          this.asFilterModel.clearFilters();
        }
      }
    });

    // A nice way to avoid watches is to define a property on an object
    Object.defineProperty(this.asFilterModel.sortFilter, 'count', {
      get: () => this.groupCount,
      set: (count) => {
        this.groupCount = count;
        this.cacheConfigViewState();
      }
    });

    Object.defineProperty(this.asFilterModel.sortFilter, 'groupBy', {
      get: () => this.groupBy,
      set: (grouping) => {
        this.groupBy = grouping;
        this.cacheConfigViewState();
      }
    });

    Object.defineProperty(this.asFilterModel.sortFilter, 'showStageDuration', {
      get: () => this.showStageDuration,
      set: (newVal) => {
        this.showStageDuration = newVal;
        this.cacheConfigViewState();
      }
    });

    this.asFilterModel.activate();
  }

  private getCachedViewState(): { count: number, groupBy: string, showDurations: boolean, showStageDuration: boolean } {
    const cached = this.configViewStateCache.get('#global') || {},
        defaults = { count: 2, groupBy: 'name', showDurations: false };
    return extend(defaults, cached);
  }

  private cacheConfigViewState(): void {
    this.configViewStateCache.put('#global', { count: this.groupCount, groupBy: this.groupBy, showStageDuration: this.showStageDuration });
  }

  private isExecutionState(stateName: string): boolean {
    return stateName === 'home.applications.application.pipelines.executions' ||
      stateName === 'home.project.application.pipelines.executions';
  }

  private isChildState(stateName: string): boolean {
    return stateName.includes('executions.execution');
  }

  private isExecutionStateOrChild(stateName: string): boolean {
    return this.isExecutionState(stateName) || this.isChildState(stateName);
  }

  private movingToExecutionsState(toState: Ng1StateDeclaration): boolean {
    return this.isExecutionStateOrChild(toState.name);
  }

  private movingFromExecutionsState (toState: Ng1StateDeclaration, fromState: Ng1StateDeclaration): boolean {
    return this.isExecutionStateOrChild(fromState.name) && !this.isExecutionStateOrChild(toState.name);
  }

  private fromApplicationListState(fromState: Ng1StateDeclaration): boolean {
    return fromState.name === 'home.applications';
  }

  private shouldRouteToSavedState(toParams: StateParams, fromState: Ng1StateDeclaration): boolean {
    return this.asFilterModel.hasSavedState(toParams) && !this.isExecutionStateOrChild(fromState.name);
  }
}

export let executionFilterModel: ExecutionFilterModel = undefined;
export const EXECUTION_FILTER_MODEL = 'spinnaker.core.delivery.filter.executionFilter.model';
module (EXECUTION_FILTER_MODEL, [
  require('core/filterModel/filter.model.service'),
  require('core/navigation/urlParser.service'),
  VIEW_STATE_CACHE_SERVICE
]).factory('executionFilterModel', ($rootScope: IRootScopeService, filterModelService: any, urlParser: any, viewStateCache: ViewStateCacheService) =>
                                    new ExecutionFilterModel($rootScope, filterModelService, urlParser, viewStateCache))
  .run(($injector: any) => executionFilterModel = <ExecutionFilterModel>$injector.get('executionFilterModel'));
