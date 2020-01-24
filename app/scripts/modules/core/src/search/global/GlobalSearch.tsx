import React from 'react';
import { UIRouterContext } from '@uirouter/react-hybrid';

import { Debounce } from 'lodash-decorators';
import { flatten } from 'lodash';
import ReactGA from 'react-ga';
import { Observable, Subject } from 'rxjs';

import { ISearchResultSet } from '../infrastructure/infrastructureSearch.service';
import { ISearchResult } from '../search.service';
import { ReactInjector } from 'core/reactShims';
import { Tooltip } from 'core/presentation/Tooltip';
import { Spinner } from 'core/widgets/spinners/Spinner';
import { searchRank } from '../searchRank.filter';
import { RecentlyViewedItems, IChildComponentProps } from '../infrastructure/RecentlyViewedItems';
import { ClusterState } from 'core/state';

import { GlobalSearchResults } from './GlobalSearchResults';
import { GlobalSearchRecentItems } from './GlobalSearchRecentItems';

const SLASH_KEY = 191;
const MIN_SEARCH_LENGTH = 3;

const isQuestionMark = ({ which, shiftKey }: KeyboardEvent) => which === SLASH_KEY && shiftKey;

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
      .debounceTime(300)
      .do(query => {
        ReactGA.event({ category: 'Global Search', action: 'Query', label: query });
        this.setState({ querying: true });
      })
      .switchMap((query: string) => Observable.fromPromise(search.query(query)))
      .map(result =>
        result
          .filter(({ results }) => results.length)
          .map(category => ({
            ...category,
            results: searchRank(category.results, category.query).slice(0, 5),
          }))
          .sort((a, b) => a.type.order - b.type.order),
      )
      .takeUntil(this.destroy$)
      .subscribe(categories => {
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
    const { target, which } = event;
    if (
      target instanceof HTMLInputElement ||
      target instanceof HTMLTextAreaElement ||
      isQuestionMark(event) ||
      which !== SLASH_KEY
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

    const { which, shiftKey } = event;

    if (which === 27) {
      // escape
      ReactGA.event({ category: 'Global Search', action: 'Keyboard Nav', label: 'escape (from input)' });
      this.searchField.blur();
    } else if (which === 40) {
      // down
      ReactGA.event({ category: 'Global Search', action: 'Keyboard Nav', label: 'arrow down (from input)' });
      event.preventDefault();
      this.focusFirstSearchResult();
    } else if (which === 38) {
      // up
      ReactGA.event({ category: 'Global Search', action: 'Keyboard Nav', label: 'arrow up (from input)' });
      event.preventDefault();
      this.focusLastSearchResult();
    } else if (which === 9) {
      // tab
      if (!shiftKey) {
        ReactGA.event({ category: 'Global Search', action: 'Keyboard Nav', label: 'tab (from input)' });
        event.preventDefault();
        this.focusFirstSearchResult();
      }
    } else if (which === 13) {
      // enter
      // do not submit the form and reload the page
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
    const { which, target } = event;
    if (which === 27) {
      // escape
      ReactGA.event({ category: 'Global Search', action: 'Keyboard Nav', label: 'escape (from result)' });
      this.setState({
        showDropdown: false,
        showMinLengthWarning: false,
        query: '',
        querying: false,
        categories: null,
      });
    } else if (which === 9) {
      // tab - let it navigate automatically, but close menu if on the last result
      const flattenedRefs = flatten(this.resultRefs);
      const lastResultRef = flattenedRefs[flattenedRefs.length - 1];
      if (target === lastResultRef) {
        ReactGA.event({ category: 'Global Search', action: 'Keyboard Nav', label: 'tab (from result)' });
        this.hideDropdown();
        return;
      }
    } else if (which === 40) {
      // down
      ReactGA.event({ category: 'Global Search', action: 'Keyboard Nav', label: 'down (from result)' });
      const flattenedRefs = flatten(this.resultRefs);
      const currentRefIndex = flattenedRefs.indexOf(target as HTMLElement);
      const nextResultRef = flattenedRefs[currentRefIndex + 1];

      nextResultRef && nextResultRef.focus();
      event.preventDefault();
    } else if (which === 38) {
      // up
      ReactGA.event({ category: 'Global Search', action: 'Keyboard Nav', label: 'up (from result)' });
      const flattenedRefs = flatten(this.resultRefs);
      const currentRefIndex = flattenedRefs.indexOf(target as HTMLElement);
      const prevResultRef = flattenedRefs[currentRefIndex - 1];

      prevResultRef && prevResultRef.focus();
      event.preventDefault();
    } else if (which === 13) {
      // enter
      ReactGA.event({ category: 'Global Search', action: 'Keyboard Nav', label: 'enter (from result)' });
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
      <li ref={ref => (this.container = ref)} className="global-search open">
        <form className="right global-search">
          <div className="form-group has-feedback">
            <div className="input-group">
              <input
                ref={ref => (this.searchField = ref)}
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
              ReactGA.event({ category: 'Global Search', action: `Recent item selected from ${category}` });
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
          ReactGA.event({ category: 'Global Search', action: 'Result Selected' });
          this.hideDropdown();
          this.clearFilters(result);
        }}
        onSeeMoreClick={() => {
          ReactGA.event({ category: 'Global Search', action: 'See all results selected' });
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
