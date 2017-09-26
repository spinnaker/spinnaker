import { BindAll } from 'lodash-decorators';
import { flatten, isEmpty } from 'lodash';
import { module, IController, ILocationService, IQService, IScope, IPromise } from 'angular';
import { StateService } from '@uirouter/core';
import { IModalService } from 'angular-ui-bootstrap';

import { Application } from 'core/application';
import { IProject } from 'core/domain';
import { IQueryParams, UrlParser } from 'core/navigation';
import { SearchFilterTypeRegistry, IFilterType } from '../widgets/SearchFilterTypeRegistry';
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

export interface IViewState {
  status: SearchStatus;
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

@BindAll()
export class InfrastructureV2Ctrl implements IController {

  public viewState: IViewState;
  public query: string;
  public menuActions: IMenuItem[];

  constructor(private $location: ILocationService,
              private $q: IQService,
              private $scope: IScope,
              private $state: StateService,
              private $uibModal: IModalService,
              private infrastructureSearchServiceV2: InfrastructureSearchServiceV2,
              private cacheInitializer: CacheInitializerService,
              private overrideRegistry: OverrideRegistry,
              pageTitleService: PageTitleService) {
    'ngInject';
    this.$scope.categories = [];
    this.$scope.projects = [];

    this.viewState = {
      status: SearchStatus.INITIAL
    };

    // just set the page title - don't try to get fancy w/ the search terms
    pageTitleService.handleRoutingSuccess({ pageTitleMain: { field: undefined, label: 'Infrastructure' }});

    this.query = UrlParser.parseLocationHash(window.location.hash);
    if (this.query.length) {
      const params = UrlParser.parseQueryString(this.query);
      this.loadNewQuery(params);
    }

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
      categoryMap[result.id] = result.results;
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

  private loadNewQuery(params: IQueryParams) {

    if (isEmpty(params)) {
      this.$scope.$applyAsync(() => {
        this.viewState.status = SearchStatus.INITIAL;
        this.$scope.categories = [];
        this.$scope.projects = [];
      });
      return;
    }

    this.viewState.status = SearchStatus.SEARCHING;
    this.infrastructureSearchServiceV2.search(params).then((results: ISearchResultSet[]) => {

      // for any registered post search result searcher, take its registered type mapping,
      // retrieve that data from the search results from the search API above, and pass to the
      // appropriate post search result searcher.
      const searchResultMap: ISearchResultSetMap = this.categorizeSearchResultSet(results);
      const promises: IPromise<ISearchResultSet[]>[] = [];
      PostSearchResultSearcherRegistry.getRegisteredTypes().forEach((mapping: ITypeMapping) => {
        if (!searchResultMap[mapping.sourceType] && !isEmpty(searchResultMap[mapping.targetType]['results'])) {
          promises.push(PostSearchResultSearcherRegistry.getPostResultSearcher(mapping.sourceType).getPostSearchResults(searchResultMap[mapping.targetType].results));
        }
      });

      this.$q.all(promises).then((postSearchResults: ISearchResultSet[][]) => {

        results = results.concat(flatten(postSearchResults));
        const categories: ISearchResultSet[] =
          results.filter((category: ISearchResultSet) => category.category !== 'Projects' && category.results.length);
        this.hydrateResults(categories);
        this.$scope.categories = categories;

        this.$scope.projects =
          results.filter((category: ISearchResultSet) => category.category === 'Projects' && category.results.length);

        if (this.$scope.categories.length || this.$scope.projects.length) {
          this.viewState.status = SearchStatus.FINISHED;
        } else {
          this.viewState.status = SearchStatus.NO_RESULTS;
        }
      });
    });
  }

  private categorizeSearchResultSet(results: ISearchResultSet[]): ISearchResultSetMap {

    return results.reduce((map: ISearchResultSetMap, result: ISearchResultSet) => {
      map[result.id] = result;
      return map;
    } , {});
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

    const params: IQueryParams = {};
    filters.slice(0).reverse().forEach(filter => params[SearchFilterTypeRegistry.getFilterByModifier(filter.modifier).key] = filter.text);
    this.$location.search(params);
    this.$location.replace();
    this.loadNewQuery(params);
  }
}

export const SEARCH_INFRASTRUCTURE_V2_CONTROLLER = 'spinnaker.search.infrastructureNew.controller';
module(SEARCH_INFRASTRUCTURE_V2_CONTROLLER, [
  INFRASTRUCTURE_SEARCH_SERVICE_V2,
  RECENT_HISTORY_SERVICE,
  PAGE_TITLE_SERVICE,
  CACHE_INITIALIZER_SERVICE,
  OVERRIDE_REGISTRY,
]).controller('InfrastructureV2Ctrl', InfrastructureV2Ctrl);
