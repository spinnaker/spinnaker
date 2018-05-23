import { extend } from 'lodash';
import { Subject } from 'rxjs';
import { $rootScope } from 'ngimport';
import { Transition } from '@uirouter/core';

import { ICache, ViewStateCache } from 'core/cache';
import { IExecutionGroup } from 'core/domain';
import { IFilterConfig, IFilterModel } from 'core/filterModel/IFilterModel';
import { FilterModelService } from 'core/filterModel';
import { UrlParser } from 'core/navigation/urlParser';
import { ReactInjector } from 'core/reactShims';

export const filterModelConfig: IFilterConfig[] = [
  { model: 'filter', param: 'q', clearValue: '', type: 'string', filterLabel: 'search' },
  { model: 'pipeline', param: 'pipeline', type: 'trueKeyObject', clearValue: {} },
  { model: 'status', type: 'trueKeyObject', clearValue: {} },
];

const GLOBAL_CACHE_KEY = '#global';

export interface IExecutionFilterModel extends IFilterModel {
  groups: IExecutionGroup[];
}
export class ExecutionFilterModel {
  // Store count globally for 180 days
  private configViewStateCache: ICache;

  private groupCount: number;
  private groupBy: string;
  private showDurations: boolean;

  public asFilterModel: IExecutionFilterModel;
  public mostRecentApplication: string;

  // This is definitely not the best way to do this, but already have a Subject in here, so just using the same
  // mechanism for now.
  public expandSubject: Subject<boolean> = new Subject<boolean>();

  constructor() {
    this.configViewStateCache = ViewStateCache.createCache('executionFilters', {
      version: 2,
      maxAge: 14 * 24 * 60 * 60 * 1000,
    });

    this.asFilterModel = FilterModelService.configureFilterModel(this as any, filterModelConfig);

    let mostRecentParams: any = null;
    // WHY??? Because, when the stateChangeStart event fires, the $location.search() will return whatever the query
    // params are on the route we are going to, so if the user is using the back button, for example, to go to the
    // Infrastructure page with a search already entered, we'll pick up whatever search was entered there, and if we
    // come back to this application's clusters view, we'll get whatever that search was.
    $rootScope.$on('$locationChangeStart', (_event, toUrl: string, fromUrl: string) => {
      const [oldBase, oldQuery] = fromUrl.split('?'),
        [newBase, newQuery] = toUrl.split('?');

      if (oldBase === newBase) {
        mostRecentParams = newQuery ? UrlParser.parseQueryString(newQuery) : {};
      } else {
        mostRecentParams = oldQuery ? UrlParser.parseQueryString(oldQuery) : {};
      }
    });

    // Wire up transition hooks
    const saveFilterState = (trans: Transition) =>
      this.asFilterModel.saveState(trans.from(), trans.params('from'), mostRecentParams);

    const applyCachedViewState = (trans: Transition) => {
      this.mostRecentApplication = trans.params().application;
      this.assignViewStateFromCache();
    };

    const updateUrl = () => this.asFilterModel.applyParamsToUrl();

    const restoreSavedState = (trans: Transition) =>
      this.asFilterModel.hasSavedState(trans.params()) && this.asFilterModel.restoreState(trans.params());

    const transitionService = ReactInjector.$uiRouter.transitionService;
    const executionsGlob = 'home.**.application.pipelines.executions.**';
    transitionService.onSuccess({ exiting: executionsGlob }, saveFilterState);
    transitionService.onSuccess({ to: executionsGlob }, applyCachedViewState);
    // Priority < 0 so it executes after the router updates the URL
    transitionService.onSuccess({ retained: executionsGlob }, updateUrl, { priority: -1 });
    transitionService.onSuccess({ entering: executionsGlob }, restoreSavedState);

    // A nice way to avoid watches is to define a property on an object
    Object.defineProperty(this.asFilterModel.sortFilter, 'count', {
      get: () => this.groupCount,
      set: count => {
        this.groupCount = count;
        this.cacheConfigViewState();
      },
    });

    Object.defineProperty(this.asFilterModel.sortFilter, 'groupBy', {
      get: () => this.groupBy,
      set: grouping => {
        this.groupBy = grouping;
        this.cacheConfigViewState();
      },
    });

    Object.defineProperty(this.asFilterModel.sortFilter, 'showDurations', {
      get: () => this.showDurations,
      set: newVal => {
        this.showDurations = newVal;
        this.cacheConfigViewState();
      },
    });

    this.asFilterModel.activate();
  }

  private assignViewStateFromCache(): void {
    const viewState = this.getCachedViewState();
    this.groupCount = viewState.count;
    this.groupBy = viewState.groupBy;
    this.showDurations = viewState.showDurations;
  }

  private getCachedViewState(key?: string): { count: number; groupBy: string; showDurations: boolean } {
    key = key || this.mostRecentApplication || GLOBAL_CACHE_KEY;
    const cachedApp = this.configViewStateCache.get(key) || {};
    const cachedGlobal = this.configViewStateCache.get(GLOBAL_CACHE_KEY) || {};
    const defaults = { count: 2, groupBy: 'name', showDurations: false };
    this.configViewStateCache.touch(key); // prevents cache from expiring just because it hasn't been changed
    return extend(defaults, cachedApp, { showDurations: cachedGlobal.showDurations });
  }

  private cacheConfigViewState(): void {
    const appCacheData = this.getCachedViewState();
    appCacheData.showDurations = this.showDurations;

    // don't cache count or groupBy if viewing only a subset of pipelines
    if (!Object.keys(this.asFilterModel.sortFilter.pipeline).length) {
      appCacheData.count = this.groupCount;
      appCacheData.groupBy = this.groupBy;
    }
    const appCacheKey = this.mostRecentApplication || GLOBAL_CACHE_KEY;
    this.configViewStateCache.put(appCacheKey, appCacheData);

    // Always cache showDurations globally
    if (appCacheKey !== GLOBAL_CACHE_KEY) {
      const globalCacheData = this.getCachedViewState(GLOBAL_CACHE_KEY);
      globalCacheData.showDurations = this.showDurations;
      this.configViewStateCache.put(GLOBAL_CACHE_KEY, globalCacheData);
    }
  }
}
