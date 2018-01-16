import * as React from 'react';
import { IPromise } from 'angular';
import { BindAll } from 'lodash-decorators';
import { flatten, keyBy, isEmpty } from 'lodash';
import { Subject } from 'rxjs';
import { MenuItem, DropdownButton, ButtonToolbar } from 'react-bootstrap';

import { ITag } from 'core/widgets';
import { ReactInjector } from 'core/reactShims';
import { Application } from 'core/application';
import { IProject } from 'core/domain';
import { IQueryParams } from 'core/navigation';

import { Search } from '../widgets';
import { RecentlyViewedItems } from '../infrastructure/RecentlyViewedItems';
import { ISearchResultSet } from '../infrastructure/infrastructureSearch.service';
import { ISearchResult } from '../search.service';
import {
  SearchResults, SearchStatus, ITypeMapping, PostSearchResultSearcherRegistry, searchResultTypeRegistry,
  ISearchResultHydrator, SearchResultHydratorRegistry
} from '../searchResult';

export interface ISearchResultSetMap {
  [key: string]: ISearchResultSet;
}

// These state parameters are passed through to Gate's search API
const API_PARAMS = ['key', 'name', 'account', 'region', 'stack'];

export interface ISearchV2State {
  status: SearchStatus;
  params: { [key: string]: any };

  categories: any[];
  projects: any[];

  refreshingCache: boolean;
}

@BindAll()
export class SearchV2 extends React.Component<{}, ISearchV2State> {
  private $q = ReactInjector.$q;
  private $rootScope = ReactInjector.$rootScope;
  private $state = ReactInjector.$state;
  private $uibModal = ReactInjector.modalService;
  private $uiRouter = ReactInjector.$uiRouter;
  private infrastructureSearchServiceV2 = ReactInjector.infrastructureSearchServiceV2;
  private cacheInitializer = ReactInjector.cacheInitializer;
  private overrideRegistry = ReactInjector.overrideRegistry;

  private searchResultTypes = searchResultTypeRegistry.getAll();
  private destroy$ = new Subject();

  constructor(props: {}) {
    super(props);

    this.state = {
      status: SearchStatus.INITIAL,
      params: {},
      categories: [],
      projects: [],
      refreshingCache: false,
    };

    // just set the page title - don't try to get fancy w/ the search terms
    ReactInjector.pageTitleService.handleRoutingSuccess({ pageTitleMain: { field: undefined, label: 'Search' } });
  }

  public componentDidMount() {
    this.$uiRouter.globals.params$
      .takeUntil(this.destroy$)
      .subscribe(params => this.loadNewQuery(params));
  }

  public componentWillUnmount() {
    this.destroy$.next();
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

  private loadNewQuery(stateParams: IQueryParams) {
    const paramValIsValid = (val: any) =>
      val !== null && val !== undefined && !(typeof val === 'string' && val.trim() === '');

    const params: IQueryParams = Object.keys(stateParams)
      .filter(key => API_PARAMS.includes(key))
      .filter(key => paramValIsValid(stateParams[key]))
      .reduce((acc, key) => ({ ...acc, [key]: stateParams[key] }), {});

    if (isEmpty(params)) {
      this.setState({ params: {}, categories: [], projects: [], status: SearchStatus.INITIAL });
      return;
    }

    this.setState({ params, status: SearchStatus.SEARCHING });

    const paramsCopy = Object.assign({}, params);
    this.infrastructureSearchServiceV2.search(paramsCopy).then((results: ISearchResultSet[]) => {
      // for any registered post search result searcher, take its registered type mapping,
      // retrieve that data from the search results from the search API above, and pass to the
      // appropriate post search result searcher.
      const searchResultMap: ISearchResultSetMap = keyBy(results, 'type.id');
      const promises: IPromise<ISearchResultSet[]>[] = [];
      PostSearchResultSearcherRegistry.getRegisteredTypes().forEach((mapping: ITypeMapping) => {
        if (!searchResultMap[mapping.sourceType] && !isEmpty(searchResultMap[mapping.targetType]['results'])) {
          const postResultSearcher = PostSearchResultSearcherRegistry.getPostResultSearcher(mapping.sourceType);
          promises.push(postResultSearcher.getPostSearchResults(searchResultMap[mapping.targetType].results));
        }
      });

      this.$q.all(promises).then((postSearchResults: ISearchResultSet[][]) => {
        results = results.concat(flatten(postSearchResults));
        const categories: ISearchResultSet[] =
          results.filter((category: ISearchResultSet) => category.type.id !== 'projects' && category.results.length);
        this.hydrateResults(categories);

        const projects = results.filter((category: ISearchResultSet) => category.type.id === 'projects' && category.results.length);

        const status = categories.length || projects.length ? SearchStatus.FINISHED : SearchStatus.NO_RESULTS;

        this.setState({ categories, projects, status });
      });
    });
  }

  private createProject() {
    this.$uibModal.open({
      scope: this.$rootScope.$new(),
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
      scope: this.$rootScope.$new(),
      templateUrl: this.overrideRegistry.getTemplate('createApplicationModal', require('../../application/modal/newapplication.html')),
      controller: this.overrideRegistry.getController('CreateApplicationModalCtrl'),
      controllerAs: 'newAppModal'
    }).result.then(this.routeToApplication).catch(() => {});
  };

  private routeToApplication(app: Application) {
    this.$state.go('home.applications.application.insight.clusters', { application: app.name });
  }

  private refreshAllCaches() {
    if (this.state.refreshingCache) {
      return;
    }

    this.setState({ refreshingCache: true });
    this.cacheInitializer.refreshCaches().then(() => {
      this.setState({ refreshingCache: false })
    });
  }

  public handleFilterChange(filters: ITag[]) {
    const newParams = filters.reduce((params, filter) => ({ ...params, [filter.key]:  filter.text }), {});
    this.$state.go('.', newParams, { location: 'replace' });
  }

  public render() {
    const { categories, projects, params, status } = this.state;
    const hasSearchQuery = Object.keys(params).length > 0;

    const DropdownActions = () => {
      const refreshText = this.state.refreshingCache ?
          <span><span className="fa fa-refresh fa-spin"/> Refreshing...</span> :
          <span>Refresh all caches</span>;

      return (
        <ButtonToolbar>
          <DropdownButton pullRight={true} bsSize="large" title="Actions" id="dropdown-size-large">
            <MenuItem href="javascript:void(0)" onClick={this.createApplication}>Create Application</MenuItem>
            <MenuItem href="javascript:void(0)" onClick={this.createProject}>Create Project</MenuItem>
            <MenuItem href="javascript:void(0)" onClick={this.refreshAllCaches}>{refreshText}</MenuItem>
          </DropdownButton>
        </ButtonToolbar>
      )
    };

    return (
      <div className="infrastructure">
        <div className="infrastructure-section search-header">
          <div className="container">
            <h2 className="header-section">
              <div className="flex-grow">
                <Search params={this.state.params} onChange={this.handleFilterChange}/>
              </div>
            </h2>
            <div className="header-actions">
              <DropdownActions/>
            </div>
          </div>
        </div>
        <div className="container flex-fill" style={{ overflowY: 'auto' }}>
          {!hasSearchQuery && (
            <div>
              <h3 style={{ textAlign: 'center' }}>Please enter a search query to get started</h3>
              <RecentlyViewedItems/>
            </div>
          )}

          {hasSearchQuery && (
            <div className="flex-fill">
              <SearchResults
                searchStatus={status}
                searchResultTypes={this.searchResultTypes}
                searchResultCategories={categories}
                searchResultProjects={projects}
              />
            </div>
          )}
        </div>
      </div>
    );
  }
}
