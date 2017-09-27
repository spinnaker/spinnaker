import { BindAll } from 'lodash-decorators';
import { isEmpty } from 'lodash';
import { module, IController, ILocationService, IScope } from 'angular';
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
import { INFRASTRUCTURE_SEARCH_SERVICE, InfrastructureSearchService } from './infrastructureSearch.service';
import { ISearchResultHydrator, SearchResultHydratorRegistry } from '../searchResult/SearchResultHydratorRegistry';
import { SearchStatus } from '../searchResult/SearchResults';
import { ISearchResultSet } from '..';
import { ISearchResult } from 'core/search/search.service';

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

@BindAll()
export class InfrastructureV2Ctrl implements IController {

  public viewState: IViewState;
  public query: string;
  public menuActions: IMenuItem[];

  constructor(private $location: ILocationService, private $scope: IScope,
              private $state: StateService, private $uibModal: IModalService,
              private infrastructureSearchService: InfrastructureSearchService,
              private cacheInitializer: CacheInitializerService, private overrideRegistry: OverrideRegistry,
              private pageTitleService: PageTitleService) {
    'ngInject';
    this.$scope.categories = [];
    this.$scope.projects = [];

    this.viewState = {
      status: SearchStatus.INITIAL
    };

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
    this.infrastructureSearchService.getSearcher().query(params).then((result: ISearchResultSet[]) => {

      const categories: ISearchResultSet[] =
        result.filter((category: ISearchResultSet) => category.category !== 'Projects' && category.results.length);
      this.hydrateResults(categories);
      this.$scope.categories = categories;

      this.$scope.projects =
        result.filter((category: ISearchResultSet) => category.category === 'Projects' && category.results.length);
      this.pageTitleService.handleRoutingSuccess(
        {
          pageTitleMain: {
            field: undefined,
            label: params ? `Search results for "${JSON.stringify(params)}"` : 'Infrastructure'
          }
        }
      );

      if (this.$scope.categories.length || this.$scope.projects.length) {
        this.viewState.status = SearchStatus.FINISHED;
      } else {
        this.viewState.status = SearchStatus.NO_RESULTS;
      }
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
    }).result.then(this.routeToProject);
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
    }).result.then(this.routeToApplication);
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
  INFRASTRUCTURE_SEARCH_SERVICE,
  RECENT_HISTORY_SERVICE,
  PAGE_TITLE_SERVICE,
  CACHE_INITIALIZER_SERVICE,
  OVERRIDE_REGISTRY,
]).controller('InfrastructureV2Ctrl', InfrastructureV2Ctrl);
