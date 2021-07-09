import { UIRouterContext } from '@uirouter/react-hybrid';
import { flatten } from 'lodash';
import { Debounce } from 'lodash-decorators';
import React from 'react';
import { from as observableFrom, Subject } from 'rxjs';
import { debounceTime, map, switchMap, takeUntil, tap } from 'rxjs/operators';

import { GlobalSearchRecentItems } from './GlobalSearchRecentItems';
import { GlobalSearchResults } from './GlobalSearchResults';
import { IChildComponentProps, RecentlyViewedItems } from '../infrastructure/RecentlyViewedItems';
import { ISearchResultSet } from '../infrastructure/infrastructureSearch.service';
import { Tooltip } from '../../presentation/Tooltip';
import { ReactInjector } from '../../reactShims';
import { ISearchResult } from '../search.service';
import { searchRank } from '../searchRank.filter';
import { ClusterState } from '../../state';
import { logger } from '../../utils';
import { findMatchingApplicationResultToQuery, getSearchQuery as getSearchQueryParams } from './utils';
import { Spinner } from '../../widgets/spinners/Spinner';

const SLASH_KEY = '/';
const MIN_SEARCH_LENGTH = 3;

const isQuestionMark = ({ key, shiftKey }: KeyboardEvent) => key === '/' && shiftKey;

export interface IGlobalSearchState {
  showDropdown: boolean;
  showMinLengthWarning: boolean;
  query: string;
  querying: boolean;
  categories: ISearchResultSet[];
}

@UIRouterContext
export class GlobalSearch extends React.Component<{}, IGlobalSearchState> {
  private container: HTMLElement;
  private searchField: HTMLInputElement;
  private resultRefs: HTMLElement[][];

  private query$ = new Subject<string>();
  private destroy$ = new Subject();

  constructor(props: {}) {
    super(props);
    this.state = {
      showDropdown: false,
      showMinLengthWarning: false,
      query: '',
      querying: false,
      categories: null,
    };
  }

  public componentDidMount() {
    window.addEventListener('keyup', this.handleWindowKeyup);
    window.addEventListener('click', this.handleWindowClick);

    const { infrastructureSearchService } = ReactInjector;
    const search = infrastructureSearchService.getSearcher();

    this.query$
      .pipe(
        debounceTime(300),
        tap((query) => {
          logger.log({ category: 'Global Search', action: 'Query', data: { label: query } });
          this.setState({ querying: true });
        }),
        switchMap((query: string) => observableFrom(search.query(query))),
        map((result) =>
          result
            .filter(({ results }) => results.length)
            .map((category) => ({
              ...category,
              results: searchRank(category.results, category.query).slice(0, 5),
            }))
            .sort((a, b) => a.type.order - b.type.order),
        ),
        takeUntil(this.destroy$),
      )
      .subscribe((categories) => {
        this.resultRefs = categories.map(() => []);

        this.setState({ querying: false, categories });
      });
  }

  public componentWillUnmount() {
    window.removeEventListener('keyup', this.handleWindowKeyup);
    window.removeEventListener('click', this.handleWindowClick);

    this.destroy$.next();
  }

  private handleWindowKeyup = (event: KeyboardEvent) => {
    const { target, key } = event;
    if (
      target instanceof HTMLInputElement ||
      target instanceof HTMLTextAreaElement ||
      isQuestionMark(event) ||
      key !== SLASH_KEY
    ) {
      return;
    }

    this.searchField.focus();
  };

  private handleWindowClick = (event: MouseEvent) => {
    if (!this.container.contains(event.target as Node)) {
      this.hideDropdown();
    }
  };

  private searchFieldBlurred = ({ relatedTarget }: React.FocusEvent<HTMLInputElement>) => {
    if (!this.container.contains(relatedTarget as Node)) {
      this.hideDropdown();
    }
  };

  private searchFieldKeyUp = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (!this.state.showDropdown) {
      return;
    }

    const { key, shiftKey } = event;

    if (key === 'Escape') {
      logger.log({ category: 'Global Search', action: 'Keyboard Nav', data: { label: 'escape (from input)' } });
      this.searchField.blur();
    } else if (key === 'ArrowDown') {
      logger.log({ category: 'Global Search', action: 'Keyboard Nav', data: { label: 'arrow down (from input)' } });
      event.preventDefault();
      this.focusFirstSearchResult();
    } else if (key === 'ArrowUp') {
      logger.log({ category: 'Global Search', action: 'Keyboard Nav', data: { label: 'arrow up (from input)' } });
      event.preventDefault();
      this.focusLastSearchResult();
    } else if (key === 'Tab') {
      if (!shiftKey) {
        logger.log({ category: 'Global Search', action: 'Keyboard Nav', data: { label: 'tab (from input)' } });
        event.preventDefault();
        this.focusFirstSearchResult();
      }
    } else if (key === 'Enter') {
      const { $state } = ReactInjector;
      if (this.state.categories) {
        const matchingQueryResult = findMatchingApplicationResultToQuery(this.state.categories, this.state.query);
        if (matchingQueryResult) {
          $state.go('home.applications.application', {
            application: matchingQueryResult.result.application,
          });
          this.hideDropdown();
        } else {
          $state.go('home.search', getSearchQueryParams(this.state.query));
        }
      }

      event.preventDefault();
    }
  };

  private focusFirstSearchResult = () => {
    const refToFocus = this.resultRefs[0] && this.resultRefs[0][0];
    refToFocus && refToFocus.focus();
  };

  private focusLastSearchResult = () => {
    const flattenedRefs = flatten(this.resultRefs);
    const refToFocus = flattenedRefs[flattenedRefs.length - 1];
    refToFocus && refToFocus.focus();
  };

  private navigateResult = (event: React.KeyboardEvent<HTMLElement>) => {
    const { key, target } = event;
    if (key === 'Escape') {
      logger.log({ category: 'Global Search', action: 'Keyboard Nav', data: { label: 'escape (from result)' } });
      this.setState({
        showDropdown: false,
        showMinLengthWarning: false,
        query: '',
        querying: false,
        categories: null,
      });
    } else if (key === 'Tab') {
      // tab - let it navigate automatically, but close menu if on the last result
      const flattenedRefs = flatten(this.resultRefs);
      const lastResultRef = flattenedRefs[flattenedRefs.length - 1];
      if (target === lastResultRef) {
        logger.log({ category: 'Global Search', action: 'Keyboard Nav', data: { label: 'tab (from result)' } });
        this.hideDropdown();
        return;
      }
    } else if (key === 'ArrowDown') {
      logger.log({ category: 'Global Search', action: 'Keyboard Nav', data: { label: 'down (from result)' } });
      const flattenedRefs = flatten(this.resultRefs);
      const currentRefIndex = flattenedRefs.indexOf(target as HTMLElement);
      const nextResultRef = flattenedRefs[currentRefIndex + 1];

      nextResultRef && nextResultRef.focus();
      event.preventDefault();
    } else if (key === 'ArrowUp') {
      logger.log({ category: 'Global Search', action: 'Keyboard Nav', data: { label: 'up (from result)' } });
      const flattenedRefs = flatten(this.resultRefs);
      const currentRefIndex = flattenedRefs.indexOf(target as HTMLElement);
      const prevResultRef = flattenedRefs[currentRefIndex - 1];

      prevResultRef && prevResultRef.focus();
      event.preventDefault();
    } else if (key === 'Enter') {
      logger.log({ category: 'Global Search', action: 'Keyboard Nav', data: { label: 'enter (from result)' } });
      // Allow keyboard event to activate the href, then hide the drop down
      setTimeout(() => this.hideDropdown(), 100);
    }
  };

  private queryChanged = ({ target }: React.ChangeEvent<HTMLInputElement>) => {
    const query = target.value;
    const { showMinLengthWarning } = this.state;
    // If the query is still too short and we've already shown a warning
    // (via the debounced considerMinLengthWarning()), keep the warning visible
    // rather than hiding it only to re-show.
    const shouldKeepWarningVisible = !!query && query.length < MIN_SEARCH_LENGTH && showMinLengthWarning;

    this.setState(
      {
        query,
        querying: false,
        showMinLengthWarning: shouldKeepWarningVisible,
        categories: null,
      },
      () => {
        if (query.length >= MIN_SEARCH_LENGTH) {
          this.query$.next(query.trim());
        } else if (!shouldKeepWarningVisible) {
          this.considerMinLengthWarning();
        }
      },
    );
  };

  // Rather than add a jarring warning message as someone is typing a query —
  // for which a warning might not even be necessary — we wait until typing has finished
  // to determine whether a warning will be useful.
  @Debounce(300)
  private considerMinLengthWarning() {
    const { query } = this.state;
    this.setState({ showMinLengthWarning: !!query && query.length < MIN_SEARCH_LENGTH });
  }

  private showDropdown = () => {
    this.setState({ showDropdown: true });
  };

  private hideDropdown = () => {
    this.setState({ showDropdown: false });
  };

  private clearFilters = (result: ISearchResult) => {
    ClusterState.filterService.overrideFiltersForUrl(result);
  };

  private renderDropdown() {
    const { query, querying, showMinLengthWarning, categories } = this.state;

    const { SpinnerDropdown, MinLengthWarning, SearchResults, RecentlyViewed } = this;

    if (!query) {
      return <RecentlyViewed />;
    }
    if (querying) {
      return <SpinnerDropdown />;
    }
    if (query.length < MIN_SEARCH_LENGTH && showMinLengthWarning) {
      return <MinLengthWarning />;
    } else if (categories) {
      return <SearchResults />;
    }

    return null;
  }

  public render() {
    const { showDropdown, query } = this.state;

    return (
      <li ref={(ref) => (this.container = ref)} className="global-search open">
        <form className="right global-search" onSubmit={(e) => e.preventDefault()}>
          <div className="form-group has-feedback">
            <div className="input-group">
              <input
                ref={(ref) => (this.searchField = ref)}
                type="search"
                className="form-control flat input-sm no-border"
                placeholder="Search"
                value={query}
                onBlur={this.searchFieldBlurred}
                onFocus={this.showDropdown}
                onChange={this.queryChanged}
                onKeyUp={this.searchFieldKeyUp}
              />
              <Tooltip
                placement="right"
                template={
                  <span>
                    Keyboard shortcut: <span className="keyboard-key">/</span>
                  </span>
                }
              >
                <i className="glyphicon glyphicon-search form-control-feedback" />
              </Tooltip>
            </div>
          </div>
        </form>
        {showDropdown && this.renderDropdown()}
      </li>
    );
  }

  private SpinnerDropdown = () => (
    <ul className="dropdown-menu" role="menu">
      <li className="horizontal middle center spinner-section">
        <Spinner size="small" />
      </li>
    </ul>
  );

  private MinLengthWarning = () => (
    <ul className="dropdown-menu" role="menu">
      <li className="horizontal middle center spinner-section">
        <span className="error-message">Please enter at least {MIN_SEARCH_LENGTH} characters</span>
      </li>
    </ul>
  );

  private RecentlyViewed = () => (
    <RecentlyViewedItems
      limit={5}
      Component={({ results }: IChildComponentProps) => {
        this.resultRefs = results.map(() => []);

        return (
          <GlobalSearchRecentItems
            categories={results}
            onItemKeyDown={this.navigateResult}
            onResultClick={(category: string) => {
              this.hideDropdown();
              logger.log({ category: 'Global Search', action: `Recent item selected from ${category}` });
            }}
            resultRef={(categoryIndex, resultIndex, ref) => {
              if (this.resultRefs[categoryIndex]) {
                this.resultRefs[categoryIndex][resultIndex] = ref;
              }
            }}
          />
        );
      }}
    />
  );

  private SearchResults = () => {
    const { query, categories } = this.state;

    return (
      <GlobalSearchResults
        categories={categories}
        query={query}
        onItemKeyDown={this.navigateResult}
        onResultClick={(result: ISearchResult) => {
          logger.log({ category: 'Global Search', action: 'Result Selected' });
          this.hideDropdown();
          this.clearFilters(result);
        }}
        onSeeMoreClick={() => {
          logger.log({ category: 'Global Search', action: 'See all results selected' });
          this.hideDropdown();
        }}
        resultRef={(categoryIndex, resultIndex, ref) => {
          if (this.resultRefs[categoryIndex]) {
            this.resultRefs[categoryIndex][resultIndex] = ref;
          }
        }}
      />
    );
  };
}
