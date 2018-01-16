import { BindAll } from 'lodash-decorators';
import { flatten, keyBy, isEmpty } from 'lodash';
import { module, IController, IQService, IScope, IPromise } from 'angular';
import { StateService } from '@uirouter/core';
import { IModalService } from 'angular-ui-bootstrap';
import { Subject } from 'rxjs';

import { Application } from 'core/application';
import { IProject } from 'core/domain';
import { IQueryParams } from 'core/navigation';
import { UIRouter } from '../../../../../../../node_modules/@uirouter/core/lib';
import { IFilterType } from '../widgets/SearchFilterTypeRegistry';
import { CACHE_INITIALIZER_SERVICE, CacheInitializerService } from 'core/cache/cacheInitializer.service';
import { OVERRIDE_REGISTRY, OverrideRegistry } from 'core/overrideRegistry/override.registry';
import { RECENT_HISTORY_SERVICE } from 'core/history/recentHistory.service';
import { PAGE_TITLE_SERVICE, PageTitleService } from 'core/pageTitle/pageTitle.service';
import { INFRASTRUCTURE_SEARCH_SERVICE_V2, InfrastructureSearchServiceV2 } from './infrastructureSearchV2.service';
import { ISearchResultHydrator, SearchResultHydratorRegistry } from '../searchResult/SearchResultHydratorRegistry';
import { SearchStatus } from '../searchResult/SearchResults';
import { ISearchResultSet } from '..';
import { ISearchResult } from 'core/search/search.service';
import {
  ITypeMapping,
  PostSearchResultSearcherRegistry
} from 'core/search/searchResult/PostSearchResultSearcherRegistry';
import { DebugTiming } from 'core/utils';
import { searchResultTypeRegistry } from 'core/search';

export interface IViewState {
  status: SearchStatus;
  params: { [key: string]: any };
}

export interface IMenuStatus {
  isOpen: boolean;
}

export interface IMenuItem {
  action?: (status: IMenuStatus) => void;
  disableAutoClose?: boolean;
  displayName: string;
}

export interface ISearchResultSetMap {
  [key: string]: ISearchResultSet;
}

const API_PARAMS = ['key', 'name', 'account', 'region', 'stack'];

@BindAll()
export class InfrastructureV2Ctrl implements IController {

  public viewState: IViewState;
  public query: string;
  public menuActions: IMenuItem[];
  public hasSearchQuery: boolean;
  public destroy$ = new Subject();

  public $onDestroy() {
    this.destroy$.next();
  }

  constructor(private $q: IQService,
              private $scope: IScope,
              private $state: StateService,
              private $uibModal: IModalService,
              $uiRouter: UIRouter,
              private infrastructureSearchServiceV2: InfrastructureSearchServiceV2,
              private cacheInitializer: CacheInitializerService,
              private overrideRegistry: OverrideRegistry,
              pageTitleService: PageTitleService) {
    'ngInject';
    this.$scope.searchResultTypes = searchResultTypeRegistry.getAll();
    this.$scope.categories = [];
    this.$scope.projects = [];

    this.viewState = {
      status: SearchStatus.INITIAL,
      params: {},
    };

    $uiRouter.globals.params$
      .takeUntil(this.destroy$)
      .subscribe(params => this.loadNewQuery(params));

    // just set the page title - don't try to get fancy w/ the search terms
    pageTitleService.handleRoutingSuccess({ pageTitleMain: { field: undefined, label: 'Search' } });

    const refreshMenuItem: IMenuItem = {
      displayName: 'Refresh all caches',
      disableAutoClose: true
    };

    refreshMenuItem.action = (status: IMenuStatus) => {
      const originalDisplayName: string = refreshMenuItem.displayName;
      refreshMenuItem.displayName = '<span class="fa fa-refresh fa-spin"></span> Refreshing...';
      this.cacheInitializer.refreshCaches().then(() => {
        refreshMenuItem.displayName = originalDisplayName;
        status.isOpen = false;
      });
    };

    this.menuActions = [
      {
        displayName: 'Create Application',
        action: this.createApplication
      },
      {
        displayName: 'Create Project',
        action: this.createProject
      },
      refreshMenuItem,
    ];
  }

  private hydrateResults(results: ISearchResultSet[]): void {
    const resultMap: { [key: string]: ISearchResult[] } =
      results.reduce((categoryMap: { [key: string]: ISearchResult[] }, result: ISearchResultSet) => {
      categoryMap[result.type.id] = result.results;
      return categoryMap;
    }, {});

    SearchResultHydratorRegistry.getHydratorKeys().forEach((hydratorKey: string) => {
      const hydrator: ISearchResultHydrator<ISearchResult> =
        SearchResultHydratorRegistry.getSearchResultHydrator(hydratorKey);
      const target: ISearchResult[] = resultMap[hydratorKey];
      if (target && hydrator) {
        hydrator.hydrate(target);
      }
    });
  }

  @DebugTiming()
  private loadNewQuery(stateParams: IQueryParams) {
    const paramValIsValid = (val: any) =>
      val !== null && val !== undefined && !(typeof val === 'string' && val.trim() === '');

    const params: IQueryParams = Object.keys(stateParams)
      .filter(key => API_PARAMS.includes(key))
      .filter(key => paramValIsValid(stateParams[key]))
      .reduce((acc, key) => ({ ...acc, [key]: stateParams[key] }), {});

    this.hasSearchQuery = !isEmpty(params);
    if (!this.hasSearchQuery) {
      this.$scope.$applyAsync(() => {
        this.viewState = { params, status: SearchStatus.INITIAL };
        this.$scope.categories = [];
        this.$scope.projects = [];
      });
      return;
    }

    this.viewState = { params, status: SearchStatus.SEARCHING };

    const paramsCopy = Object.assign({}, params);
    this.infrastructureSearchServiceV2.search(paramsCopy).then((results: ISearchResultSet[]) => {

      // for any registered post search result searcher, take its registered type mapping,
      // retrieve that data from the search results from the search API above, and pass to the
      // appropriate post search result searcher.
      const searchResultMap: ISearchResultSetMap = keyBy(results, 'type.id');
      const promises: IPromise<ISearchResultSet[]>[] = [];
      PostSearchResultSearcherRegistry.getRegisteredTypes().forEach((mapping: ITypeMapping) => {
        if (!searchResultMap[mapping.sourceType] && !isEmpty(searchResultMap[mapping.targetType]['results'])) {
          promises.push(PostSearchResultSearcherRegistry.getPostResultSearcher(mapping.sourceType).getPostSearchResults(searchResultMap[mapping.targetType].results));
        }
      });

      this.$q.all(promises).then((postSearchResults: ISearchResultSet[][]) => {
        results = results.concat(flatten(postSearchResults));
        const categories: ISearchResultSet[] =
          results.filter((category: ISearchResultSet) => category.type.id !== 'projects' && category.results.length);
        this.hydrateResults(categories);
        this.$scope.categories = categories;

        this.$scope.projects =
          results.filter((category: ISearchResultSet) => category.type.id === 'projects' && category.results.length);

        if (this.$scope.categories.length || this.$scope.projects.length) {
          this.viewState = { ...this.viewState, status: SearchStatus.FINISHED };
        } else {
          this.viewState = { ...this.viewState, status: SearchStatus.NO_RESULTS };
        }
      });
    });
  }

  private createProject() {
    this.$uibModal.open({
      scope: this.$scope,
      templateUrl: require('../../projects/configure/configureProject.modal.html'),
      controller: 'ConfigureProjectModalCtrl',
      controllerAs: 'ctrl',
      size: 'lg',
      resolve: {
        projectConfig: () => {
          return {};
        },
      }
    }).result.then(this.routeToProject).catch(() => {});
  };

  private routeToProject(project: IProject) {
    this.$state.go('home.project.dashboard', { project: project.name });
  }

  private createApplication() {
    this.$uibModal.open({
      scope: this.$scope,
      templateUrl: this.overrideRegistry.getTemplate('createApplicationModal', require('../../application/modal/newapplication.html')),
      controller: this.overrideRegistry.getController('CreateApplicationModalCtrl'),
      controllerAs: 'newAppModal'
    }).result.then(this.routeToApplication).catch(() => {});
  };

  private routeToApplication(app: Application) {
    this.$state.go('home.applications.application.insight.clusters', { application: app.name });
  }

  public handleFilterChange(filters: IFilterType[]) {
    const newParams = filters.reduce((params, filter) => ({ ...params, [filter.key]:  filter.text }), {});
    this.$state.go('.', newParams, { location: 'replace' });
  }
}

export const SEARCH_INFRASTRUCTURE_V2_CONTROLLER = 'spinnaker.search.infrastructureNew.controller';
module(SEARCH_INFRASTRUCTURE_V2_CONTROLLER, [
  INFRASTRUCTURE_SEARCH_SERVICE_V2,
  RECENT_HISTORY_SERVICE,
  PAGE_TITLE_SERVICE,
  CACHE_INITIALIZER_SERVICE,
  OVERRIDE_REGISTRY,
]).controller('InfrastructureV2Ctrl', InfrastructureV2Ctrl)
  .directive('infrastructureSearchV2', function() {
    return {
      restrict: 'E',
      templateUrl: require('./infrastructureV2.html'),
      controller: 'InfrastructureV2Ctrl',
      controllerAs: 'ctrl',
    }
  });
