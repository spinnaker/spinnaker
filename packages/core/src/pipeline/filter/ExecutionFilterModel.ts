import { extend } from 'lodash';
import { Subject } from 'rxjs';

import { ICache, ViewStateCache } from '../../cache';
import { SETTINGS } from '../../config/settings';
import { IExecutionGroup } from '../../domain';
import { FilterModelService } from '../../filterModel';
import { IFilterConfig, IFilterModel } from '../../filterModel/IFilterModel';
import { ReactInjector } from '../../reactShims';

export const filterModelConfig: IFilterConfig[] = [
  { model: 'awaitingJudgement', type: 'boolean', clearValue: false },
  { model: 'filter', param: 'q', clearValue: '', type: 'string', filterLabel: 'search' },
  { model: 'pipeline', param: 'pipeline', type: 'trueKeyObject', clearValue: {} },
  { model: 'status', type: 'trueKeyObject', clearValue: {} },
  { model: 'tags', type: 'trueKeyObject', clearValue: {} },
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
    const { transitionService } = ReactInjector.$uiRouter;

    this.configViewStateCache = ViewStateCache.createCache('executionFilters', {
      version: 2,
      maxAge: SETTINGS.maxPipelineAgeDays * 24 * 60 * 60 * 1000,
    });

    this.asFilterModel = FilterModelService.configureFilterModel(this as any, filterModelConfig);
    FilterModelService.registerRouterHooks(this.asFilterModel, '**.application.pipelines.executions.**');
    this.asFilterModel.activate();

    transitionService.onBefore(
      {
        entering: (state) =>
          !!(
            state.self.name.match('.application.pipelines.executions$') ||
            state.self.name.match('.application.pipelines.executionDetails.execution$')
          ),
      },
      (trans) => {
        this.mostRecentApplication = trans.params().application;
        this.assignViewStateFromCache();
      },
    );

    // A nice way to avoid watches is to define a property on an object
    Object.defineProperty(this.asFilterModel.sortFilter, 'count', {
      get: () => this.groupCount,
      set: (count) => {
        this.groupCount = count;
        this.cacheConfigViewState();
      },
    });

    Object.defineProperty(this.asFilterModel.sortFilter, 'groupBy', {
      get: () => this.groupBy,
      set: (grouping) => {
        this.groupBy = grouping;
        this.cacheConfigViewState();
      },
    });

    Object.defineProperty(this.asFilterModel.sortFilter, 'showDurations', {
      get: () => this.showDurations,
      set: (newVal) => {
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
    const defaults = { count: 2, groupBy: 'name', showDurations: true };
    this.configViewStateCache.touch(key); // prevents cache from expiring just because it hasn't been changed
    return extend(
      defaults,
      cachedApp,
      cachedGlobal.showDurations != null ? { showDurations: cachedGlobal.showDurations } : undefined,
    );
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
