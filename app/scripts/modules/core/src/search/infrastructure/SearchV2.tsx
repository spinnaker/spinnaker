import * as React from 'react';
import { BindAll } from 'lodash-decorators';
import { pickBy, isEmpty } from 'lodash';
import { Observable, Subject } from 'rxjs';
import { MenuItem, DropdownButton, ButtonToolbar } from 'react-bootstrap';

import { ITag } from 'core/widgets';
import { ReactInjector } from 'core/reactShims';
import { Application } from 'core/application';
import { IProject } from 'core/domain';
import { IQueryParams } from 'core/navigation';

import { Search } from '../widgets';
import { RecentlyViewedItems } from '../infrastructure/RecentlyViewedItems';
import { ISearchResultSet } from '../infrastructure/infrastructureSearch.service';
import { SearchResults, SearchStatus, searchResultTypeRegistry } from '../searchResult';

// These state parameters are passed through to Gate's search API
const API_PARAMS = ['key', 'name', 'account', 'region', 'stack'];

export interface ISearchV2State {
  selectedTab: string;
  params: { [key: string]: any };
  resultSets: ISearchResultSet[];
  refreshingCache: boolean;
}

@BindAll()
export class SearchV2 extends React.Component<{}, ISearchV2State> {
  private $rootScope = ReactInjector.$rootScope;
  private $state = ReactInjector.$state;
  private $uibModal = ReactInjector.modalService;
  private $uiRouter = ReactInjector.$uiRouter;
  private infrastructureSearchServiceV2 = ReactInjector.infrastructureSearchServiceV2;
  private cacheInitializer = ReactInjector.cacheInitializer;
  private overrideRegistry = ReactInjector.overrideRegistry;

  private searchResultTypes = searchResultTypeRegistry.getAll();

  private INITIAL_RESULTS: ISearchResultSet[] =
    this.searchResultTypes.map(type => ({ type, status: SearchStatus.SEARCHING, results: [] }));

  private destroy$ = new Subject();

  constructor(props: {}) {
    super(props);

    this.state = {
      selectedTab: this.$state.params.tab || 'applications',
      params: {},
      resultSets: this.INITIAL_RESULTS,
      refreshingCache: false,
    };

    // just set the page title - don't try to get fancy w/ the search terms
    ReactInjector.pageTitleService.handleRoutingSuccess({ pageTitleMain: { field: undefined, label: 'Search' } });
  }

  // returns parameter values that are OK to send through to the back end search API as filters
  private getApiFilterParams(params: IQueryParams): IQueryParams {
    const isValidApiParam = (val: any, key: string) => {
      return API_PARAMS.includes(key) &&
        val !== null &&
        val !== undefined &&
        !(typeof val === 'string' && val.trim() === '');
    };

    return pickBy(params, isValidApiParam);
  }

  public componentDidMount() {
    this.$uiRouter.globals.params$
      .map(stateParams => this.getApiFilterParams(stateParams))
      .do((params: IQueryParams) => this.setState({ params }))
      .distinctUntilChanged((a, b) => API_PARAMS.every(key => a[key] === b[key]))
      .do(() => this.setState({ resultSets: this.INITIAL_RESULTS }))
      // Got new params... fire off new queries for each backend
      // Use switchMap so new queries cancel any pending previous queries
      .switchMap((params: IQueryParams): Observable<ISearchResultSet[]> => {
        if (isEmpty(params)) {
          return Observable.empty();
        }

        // Start fetching results for each search type from the search service.
        // Update the overall results with the results for each search type.
        return this.infrastructureSearchServiceV2.search(Object.assign({}, params))
          .scan((acc: ISearchResultSet[], resultSet: ISearchResultSet): ISearchResultSet[] => {
            const status = resultSet.status === SearchStatus.SEARCHING ? SearchStatus.FINISHED : resultSet.status;
            resultSet = { ...resultSet, status };
            // Replace the result set placeholder with the results for this type
            return acc.filter(set => set.type !== resultSet.type).concat(resultSet);
          }, this.INITIAL_RESULTS)
      })
      .takeUntil(this.destroy$)
      .subscribe(resultSets => this.setState({ resultSets }));

    this.$uiRouter.globals.params$
      .map(params => params.tab)
      .distinctUntilChanged()
      .takeUntil(this.destroy$)
      .subscribe(selectedTab => this.setState({ selectedTab }));
  }

  public componentWillUnmount() {
    this.destroy$.next();
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
    const blankApiParams = API_PARAMS.reduce((acc, key) => ({ ...acc, [key]: undefined }), {});
    const newParams = filters.reduce((params, filter) => ({ ...params, [filter.key]:  filter.text }), blankApiParams);
    this.$state.go('.', newParams, { location: 'replace' });
  }

  public render() {
    const { params, resultSets, selectedTab } = this.state;
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
              <SearchResults selectedTab={selectedTab} resultSets={resultSets} />
            </div>
          )}
        </div>
      </div>
    );
  }
}
