import { isEmpty, pickBy } from 'lodash';
import React from 'react';
import type { Observable } from 'rxjs';
import { empty as observableEmpty, Subject } from 'rxjs';
import { distinctUntilChanged, map, scan, switchMap, takeUntil, tap } from 'rxjs/operators';
import type { IDeckRuntimeServicesInjectedProps } from '../../bootstrap/DeckRuntimeContext';
import { withDeckRuntimeServices } from '../../bootstrap/DeckRuntimeContext';

import { RecentlyViewedItems } from '../infrastructure/RecentlyViewedItems';
import { SearchResultPods } from '../infrastructure/SearchResultPods';
import type { ISearchResultSet } from '../infrastructure/infrastructureSearch.service';
import { InfrastructureSearchServiceV2 } from '../infrastructure/infrastructureSearchV2.service';
import { InsightMenu } from '../../insight/InsightMenu';
import type { IQueryParams } from '../../navigation';
import type { IRouterInjectedProps } from '../../navigation/routerContext';
import { withRouter } from '../../navigation/routerContext';
import { SearchResults, searchResultTypeRegistry, SearchStatus } from '../searchResult';
import type { ITag } from '../../widgets';
import { Search } from '../widgets';

// These state parameters are passed through to Gate's search API
const API_PARAMS = ['key', 'name', 'account', 'region', 'stack'];

export const getInfrastructureSearchApiFilterParams = (params: IQueryParams): IQueryParams =>
  pickBy(
    params,
    (value, key) =>
      API_PARAMS.includes(key) &&
      value !== null &&
      value !== undefined &&
      !(typeof value === 'string' && value.trim() === ''),
  );

export const selectSearchResultTab = (resultSets: ISearchResultSet[]): string | null => {
  const order = (resultSet: ISearchResultSet) => (resultSet.type.id === 'applications' ? -1 : resultSet.type.order);
  const tabs = resultSets.slice().sort((a, b) => order(a) - order(b));
  return tabs.reduce(
    (selection, tab) => {
      if (selection.tabId || selection.unfinished) {
        return selection;
      }
      return { unfinished: tab.status !== SearchStatus.FINISHED, tabId: tab.results.length ? tab.type.id : null };
    },
    { tabId: null as string | null, unfinished: false },
  ).tabId;
};

export const getInfrastructureSearchParamsFromFilters = (filters: ITag[]): IQueryParams => {
  const blankParams = API_PARAMS.reduce((params, key) => ({ ...params, [key]: undefined }), {});
  return filters.reduce((params, filter) => ({ ...params, [filter.key]: filter.text }), blankParams);
};

export interface ISearchV2State {
  selectedTab: string;
  params: { [key: string]: any };
  resultSets: ISearchResultSet[];
  isSearching: boolean;
  refreshingCache: boolean;
}

type SearchV2Props = IRouterInjectedProps & IDeckRuntimeServicesInjectedProps;

export class SearchV2Component extends React.Component<SearchV2Props, ISearchV2State> {
  private searchResultTypes = searchResultTypeRegistry.getAll();

  private INITIAL_RESULTS: ISearchResultSet[] = this.searchResultTypes.map((type) => ({
    type,
    status: SearchStatus.SEARCHING,
    results: [],
  }));

  private destroy$ = new Subject<void>();

  constructor(props: SearchV2Props) {
    super(props);

    this.state = {
      selectedTab: props.stateParams.tab,
      params: {},
      resultSets: this.INITIAL_RESULTS,
      isSearching: false,
      refreshingCache: false,
    };

    // just set the page title - don't try to get fancy w/ the search terms
    props.deckRuntimeServices.pageTitleService.handleRoutingSuccess({
      pageTitleMain: { field: undefined, label: 'Search' },
    });
  }

  public componentDidMount() {
    // auto-navigation only happens via shortcut links, and we only do it if there is exactly one result, e.g
    // when searching for an instance ID
    const autoNavigate = window.location.href.endsWith('route=true');
    this.props.router.globals.params$
      .pipe(
        map(getInfrastructureSearchApiFilterParams),
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

    this.props.router.globals.params$
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
    const selectedTab = selectSearchResultTab(resultSets);
    if (selectedTab) {
      this.props.stateService.go('.', { tab: selectedTab });
    }
  }

  public componentWillUnmount() {
    this.destroy$.next();
  }

  public handleFilterChange = (filters: ITag[]) => {
    this.props.stateService.go('.', getInfrastructureSearchParamsFromFilters(filters), { location: 'replace' });
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

export const SearchV2 = withDeckRuntimeServices(withRouter(SearchV2Component));
