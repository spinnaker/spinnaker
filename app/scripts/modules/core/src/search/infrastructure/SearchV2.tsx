import * as React from 'react';
import { BindAll } from 'lodash-decorators';
import { pickBy, isEmpty } from 'lodash';
import { Observable, Subject } from 'rxjs';

import { ITag } from 'core/widgets';
import { ReactInjector } from 'core/reactShims';
import { IQueryParams } from 'core/navigation';
import { InsightMenu } from 'core/insight/InsightMenu';

import { Search } from '../widgets';
import { RecentlyViewedItems } from '../infrastructure/RecentlyViewedItems';
import { ISearchResultSet } from '../infrastructure/infrastructureSearch.service';
import { SearchResults, SearchStatus, searchResultTypeRegistry } from '../searchResult';
import { SearchResultPods } from '../infrastructure/SearchResultPods';

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
  private $state = ReactInjector.$state;
  private $uiRouter = ReactInjector.$uiRouter;
  private infrastructureSearchServiceV2 = ReactInjector.infrastructureSearchServiceV2;

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


  public handleFilterChange(filters: ITag[]) {
    const blankApiParams = API_PARAMS.reduce((acc, key) => ({ ...acc, [key]: undefined }), {});
    const newParams = filters.reduce((params, filter) => ({ ...params, [filter.key]:  filter.text }), blankApiParams);
    this.$state.go('.', newParams, { location: 'replace' });
  }

  public render() {
    const { params, resultSets, selectedTab } = this.state;
    const hasSearchQuery = Object.keys(params).length > 0;


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
              <InsightMenu />
            </div>
          </div>
        </div>
        <div className="container flex-fill" style={{ overflowY: 'auto' }}>
          {!hasSearchQuery && (
            <div>
              <h3 style={{ textAlign: 'center' }}>Please enter a search query to get started</h3>
              <RecentlyViewedItems Component={SearchResultPods}/>
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
