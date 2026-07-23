import React from 'react';
import { from as observableFrom, of as observableOf, Subject } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap, takeUntil } from 'rxjs/operators';

import { ProjectSummaryPod } from './ProjectSummaryPod';
import { RecentlyViewedItems } from './RecentlyViewedItems';
import { SearchResult } from './SearchResult';
import { SearchResultPods } from './SearchResultPods';
import type { IDeckRuntimeServicesInjectedProps } from '../../bootstrap/DeckRuntimeContext';
import { withDeckRuntimeServices } from '../../bootstrap/DeckRuntimeContext';
import type { ISearchResultSet } from './infrastructureSearch.service';
import { InsightMenu } from '../../insight/InsightMenu';
import type { IRouterInjectedProps } from '../../navigation/routerContext';
import { withRouter } from '../../navigation/routerContext';
import type { ISearchResult } from '../search.service';
import { SearchService } from '../search.service';
import { searchRank } from '../searchRank.filter';
import { FirewallLabels } from '../../securityGroup/label';
import { ClusterState } from '../../state';
import { Spinner } from '../../widgets';

const MIN_SEARCH_LENGTH = 3;
const QUERY_DEBOUNCE_MS = 300;

export interface ISearchV1State {
  categories: ISearchResultSet[];
  moreResults: boolean;
  projects: ISearchResultSet[];
  query: string;
  searching: boolean;
  showMinLengthWarning: boolean;
}

export class SearchV1Component extends React.Component<
  IRouterInjectedProps & IDeckRuntimeServicesInjectedProps,
  ISearchV1State
> {
  private search = this.props.deckRuntimeServices.infrastructureSearchService.getSearcher();
  private autoNavigateQuery: string | null = this.props.stateParams.route ? this.props.stateParams.q || '' : null;
  private destroy$ = new Subject<void>();
  private query$ = new Subject<string>();

  public state: ISearchV1State = {
    categories: [],
    moreResults: false,
    projects: [],
    query: this.props.stateParams.q || '',
    searching: false,
    showMinLengthWarning: false,
  };

  public componentDidMount(): void {
    this.query$
      .pipe(
        debounceTime(QUERY_DEBOUNCE_MS),
        switchMap((query) =>
          observableFrom(this.search.query(query)).pipe(
            map((resultSets) => ({ query, resultSets })),
            catchError(() => observableOf({ query, resultSets: [] as ISearchResultSet[] })),
          ),
        ),
        takeUntil(this.destroy$),
      )
      .subscribe(({ query, resultSets }) => this.handleResults(query, resultSets));

    this.props.router.globals.params$
      .pipe(
        map((params) => params.q || ''),
        distinctUntilChanged(),
        takeUntil(this.destroy$),
      )
      .subscribe((query) => {
        if (query !== this.state.query) {
          this.handleQueryChange(query);
        }
      });

    if (this.autoNavigateQuery !== null) {
      this.props.stateService.go('.', { route: null }, { location: 'replace' });
    }

    this.updatePageTitle(this.state.query);
    this.queueQuery(this.state.query);
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  public handleQueryChange = (query: string): void => {
    if (query !== this.state.query) {
      this.autoNavigateQuery = null;
    }
    this.setState({ categories: [], moreResults: false, projects: [], query });
    this.queueQuery(query);
  };

  private queueQuery(query: string): void {
    const showMinLengthWarning = Boolean(query && query.length < MIN_SEARCH_LENGTH);
    if (!query || showMinLengthWarning) {
      this.autoNavigateQuery = null;
      this.setState({ searching: false, showMinLengthWarning });
      this.updateLocation(query);
      this.updatePageTitle(query);
      return;
    }

    this.setState({ searching: true, showMinLengthWarning: false });
    this.query$.next(query);
  }

  private handleResults(query: string, resultSets: ISearchResultSet[]): void {
    if (query !== this.state.query) {
      return;
    }

    const allResults = resultSets.reduce(
      (results, resultSet) => results.concat(resultSet.results),
      [] as ISearchResult[],
    );
    if (this.autoNavigateQuery === query && allResults.length === 1) {
      this.autoNavigateQuery = null;
      this.navigateToResult(allResults[0].href);
      return;
    }
    this.autoNavigateQuery = null;

    const categories = resultSets
      .filter(({ results, type }) => type.id !== 'projects' && results.length > 0)
      .sort((a, b) => a.type.id.localeCompare(b.type.id));
    const projects = resultSets.filter(({ results, type }) => type.id === 'projects' && results.length > 0);

    this.setState({
      categories,
      moreResults: allResults.length === SearchService.DEFAULT_PAGE_SIZE,
      projects,
      searching: false,
    });
    this.updateLocation(query);
    this.updatePageTitle(query);
  }

  private updateLocation(query: string): void {
    this.props.stateService.go('.', { q: query || null, route: null }, { location: 'replace' });
  }

  private navigateToResult(href: string): void {
    window.location.href = href;
  }

  private updatePageTitle(query: string): void {
    this.props.deckRuntimeServices.pageTitleService.handleRoutingSuccess({
      pageTitleMain: { field: undefined, label: query ? ` search results for "${query}"` : 'Infrastructure' },
    });
  }

  private resultClicked = (result: ISearchResult): void => ClusterState.filterService.overrideFiltersForUrl(result);

  private renderProjects(): React.ReactNode {
    if (!this.state.projects.length) {
      return null;
    }

    return (
      <div className="col-md-3">
        <h3>Projects</h3>
        {this.state.projects[0].results.map((project: any) => (
          <ProjectSummaryPod
            key={project.id || project.name}
            id={project.id || project.name}
            projectName={project.name}
            applications={project.config?.applications || []}
            onResultClick={() => undefined}
          />
        ))}
      </div>
    );
  }

  private renderCategories(): React.ReactNode {
    const { categories, projects, query } = this.state;
    return (
      <div className={`col-md-${projects.length ? 9 : 12}`}>
        <h3>Results matching "{query}"</h3>
        {!categories.length && <h4>No Infrastructure results found.</h4>}
        <div className="row">
          {categories.map((category) => (
            <div className="col-md-4 category-container" key={category.type.id}>
              <div className="panel category row">
                <div className="summary">
                  <span className="category-icon">
                    {category.type.iconClass && <span className={category.type.iconClass} />}
                  </span>
                  {category.type.displayName} ({category.results.length})
                </div>
                <div className="details-container list-group">
                  {searchRank(category.results, query).map((result, index) => (
                    <a
                      className="list-group-item"
                      href={result.href}
                      key={result.href || `${result.displayName}-${index}`}
                      onClick={() => this.resultClicked(result)}
                    >
                      <SearchResult displayName={result.displayName} account={(result as any).account} />
                    </a>
                  ))}
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    );
  }

  public render(): React.ReactNode {
    const { categories, moreResults, projects, query, searching, showMinLengthWarning } = this.state;
    const hasResults = categories.length > 0 || projects.length > 0;
    const showNoResults = !searching && query.length >= MIN_SEARCH_LENGTH && !hasResults;
    const showRecentResults = !searching && !query;
    const placeholder = `projects, applications, clusters, load balancers, server groups, ${FirewallLabels.get(
      'firewalls',
    )}`;

    return (
      <div className="infrastructure">
        <div className="infrastructure-section search-header">
          <div className="container">
            <h2 className="header-section">
              <i className="fa fa-search" />
              <span className="search-label">Search</span>
              <div className="flex-grow">
                <input
                  aria-label="Search infrastructure"
                  autoFocus
                  className="form-control input-lg"
                  data-purpose="search-v1-input"
                  onChange={(event) => this.handleQueryChange(event.target.value)}
                  placeholder={placeholder}
                  type="search"
                  value={query}
                />
                {showMinLengthWarning && (
                  <p data-purpose="search-v1-min-length" style={{ margin: '10px 20px 0', fontSize: '1.5rem' }}>
                    Please enter at least {MIN_SEARCH_LENGTH} characters to begin searching.
                  </p>
                )}
              </div>
            </h2>
            <div className="header-actions">
              <InsightMenu createApp={true} createProject={true} refreshCaches={false} />
            </div>
          </div>
        </div>

        {hasResults && (
          <div className="container">
            {moreResults && <p style={{ margin: '10px 20px 0' }}>Only showing the first 500 results found.</p>}
            <div className="row infrastructure-section">
              {this.renderProjects()}
              {this.renderCategories()}
            </div>
          </div>
        )}

        {searching && (
          <div className="horizontal center middle flex-1" style={{ margin: '25px 0', height: '100%' }}>
            <Spinner size="large" />
          </div>
        )}

        {showNoResults && (
          <div className="container">
            <h4 className="no-results">No results matching "{query}".</h4>
          </div>
        )}

        {showRecentResults && (
          <div className="infrastructure-section container">
            <RecentlyViewedItems Component={SearchResultPods} />
          </div>
        )}
      </div>
    );
  }
}

export const SearchV1 = withDeckRuntimeServices(withRouter(SearchV1Component));
