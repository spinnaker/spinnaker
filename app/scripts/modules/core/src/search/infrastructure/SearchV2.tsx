import { isEmpty, pickBy } from 'lodash';
import React from 'react';
import { empty as observableEmpty, Observable, Subject } from 'rxjs';
import { distinctUntilChanged, map, scan, switchMap, takeUntil, tap } from 'rxjs/operators';

import { RecentlyViewedItems } from '../infrastructure/RecentlyViewedItems';
import { SearchResultPods } from '../infrastructure/SearchResultPods';
import { ISearchResultSet } from '../infrastructure/infrastructureSearch.service';
import { InfrastructureSearchServiceV2 } from '../infrastructure/infrastructureSearchV2.service';
import { InsightMenu } from '../../insight/InsightMenu';
import { IQueryParams } from '../../navigation';
import { ReactInjector } from '../../reactShims';
import { SearchResults, searchResultTypeRegistry, SearchStatus } from '../searchResult';
import { ITag } from '../../widgets';
import { Search } from '../widgets';

// These state parameters are passed through to Gate's search API
const API_PARAMS = ['key', 'name', 'account', 'region', 'stack'];

export interface ISearchV2State {
  selectedTab: string;
  params: { [key: string]: any };
  resultSets: ISearchResultSet[];
  isSearching: boolean;
  refreshingCache: boolean;
}

export class SearchV2 extends React.Component<{}, ISearchV2State> {
  private $state = ReactInjector.$state;
  private $uiRouter = ReactInjector.$uiRouter;

  private searchResultTypes = searchResultTypeRegistry.getAll();

  private INITIAL_RESULTS: ISearchResultSet[] = this.searchResultTypes.map((type) => ({
    type,
    status: SearchStatus.SEARCHING,
    results: [],
  }));

  private destroy$ = new Subject();

  constructor(props: {}) {
    super(props);

    this.state = {
      selectedTab: this.$state.params.tab,
      params: {},
      resultSets: this.INITIAL_RESULTS,
      isSearching: false,
      refreshingCache: false,
    };

    // just set the page title - don't try to get fancy w/ the search terms
    ReactInjector.pageTitleService.handleRoutingSuccess({ pageTitleMain: { field: undefined, label: 'Search' } });
  }

  // returns parameter values that are OK to send through to the back end search API as filters
  private getApiFilterParams(params: IQueryParams): IQueryParams {
    const isValidApiParam = (val: any, key: string) => {
      return (
        API_PARAMS.includes(key) && val !== null && val !== undefined && !(typeof val === 'string' && val.trim() === '')
      );
    };

    return pickBy(params, isValidApiParam);
  }

  public componentDidMount() {
    // auto-navigation only happens via shortcut links, and we only do it if there is exactly one result, e.g
    // when searching for an instance ID
    const autoNavigate = window.location.href.endsWith('route=true');
    this.$uiRouter.globals.params$
      .pipe(
        map((stateParams) => this.getApiFilterParams(stateParams)),
        tap((params: IQueryParams) => this.setState({ params })),
        distinctUntilChanged((a, b) => API_PARAMS.every((key) => a[key] === b[key])),
        tap(() => this.setState({ resultSets: this.INITIAL_RESULTS, isSearching: true })),
        // Got new params... fire off new queries for each backend
        // Use switchMap so new queries cancel any pending previous queries
        switchMap(
          (params: IQueryParams): Observable<ISearchResultSet[]> => {
            if (isEmpty(params)) {
              return observableEmpty();
            }

            // Start fetching results for each search type from the search service.
            // Update the overall results with the results for each search type.
            return InfrastructureSearchServiceV2.search({ ...params }).pipe(
              scan((acc: ISearchResultSet[], resultSet: ISearchResultSet): ISearchResultSet[] => {
                const status = resultSet.status === SearchStatus.SEARCHING ? SearchStatus.FINISHED : resultSet.status;
                resultSet = { ...resultSet, status };
                // Replace the result set placeholder with the results for this type
                return acc.filter((set) => set.type !== resultSet.type).concat(resultSet);
              }, this.INITIAL_RESULTS),
            );
          },
        ),
        takeUntil(this.destroy$),
      )
      .subscribe(
        (resultSets) => {
          const finishedSearching = resultSets.map((r) => r.status).every((s) => s === SearchStatus.FINISHED);
          if (finishedSearching && autoNavigate) {
            const allResults = resultSets.reduce((acc, rs) => acc.concat(rs.results), []);
            if (allResults.length === 1) {
              window.location.href = allResults[0].href;
              return;
            }
          }
          if (!this.state.selectedTab) {
            this.selectTab(resultSets);
          }
          this.setState({ resultSets, isSearching: !finishedSearching });
        },
        null,
        () => this.setState({ isSearching: false }),
      );

    this.$uiRouter.globals.params$
      .pipe(
        map((params) => params.tab),
        distinctUntilChanged(),
        takeUntil(this.destroy$),
      )
      .subscribe((selectedTab) => this.setState({ selectedTab }));
  }

  /** Select the first tab with results */
  private selectTab(resultSets: ISearchResultSet[]): void {
    // Prioritize applications tab over all others
    const order = (rs: ISearchResultSet) => (rs.type.id === 'applications' ? -1 : rs.type.order);
    const tabs = resultSets.slice().sort((a, b) => order(a) - order(b));

    // Scan all tabs in order.  Find the first tab that has results.  Stop scanning when a tab with unfinished results is encountered.
    const found = tabs.reduce(
      (previous, tab) => {
        const resultAlreadyFound = previous.tabId || previous.unfinished;
        const unfinished = tab.status !== SearchStatus.FINISHED;
        const tabId = tab.results.length ? tab.type.id : null;
        return resultAlreadyFound ? previous : { ...previous, unfinished, tabId };
      },
      { tabId: null, unfinished: false },
    );

    if (found.tabId) {
      this.$state.go('.', { tab: found.tabId });
    }
  }

  public componentWillUnmount() {
    this.destroy$.next();
  }

  public handleFilterChange = (filters: ITag[]) => {
    const blankApiParams = API_PARAMS.reduce((acc, key) => ({ ...acc, [key]: undefined }), {});
    const newParams = filters.reduce((params, filter) => ({ ...params, [filter.key]: filter.text }), blankApiParams);
    this.$state.go('.', newParams, { location: 'replace' });
  };

  public render() {
    const { params, resultSets, selectedTab, isSearching } = this.state;
    const hasSearchQuery = Object.keys(params).length > 0;

    return (
      <div className="infrastructure">
        <div className="infrastructure-section search-header">
          <div className="container">
            <h2 className="header-section">
              <div className="flex-grow">
                <Search params={this.state.params} onChange={this.handleFilterChange} />
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
              <RecentlyViewedItems Component={SearchResultPods} />
            </div>
          )}

          {hasSearchQuery && (
            <div className="flex-fill">
              <SearchResults selectedTab={selectedTab} resultSets={resultSets} isSearching={isSearching} />
            </div>
          )}
        </div>
      </div>
    );
  }
}
