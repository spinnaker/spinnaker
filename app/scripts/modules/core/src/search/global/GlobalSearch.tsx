import * as React from 'react';
import { BindAll, Debounce } from 'lodash-decorators';
import { flatten, range } from 'lodash';
import * as ReactGA from 'react-ga';
import { UISref } from '@uirouter/react';
import { UIRouterContext } from '@uirouter/react-hybrid';

import { SETTINGS } from 'core/config/settings';
import { ISearchResultSet } from 'core/search/infrastructure/infrastructureSearch.service';
import { ISearchResult } from 'core/search/search.service';
import { ReactInjector } from 'core/reactShims';
import { Tooltip } from 'core/presentation/Tooltip';
import { Spinner } from 'core/widgets/spinners/Spinner';
import { SearchResult } from 'core/search/infrastructure/SearchResult';
import { searchRank } from 'core/search/searchRank.filter';
import { RecentlyViewedItems } from 'core/search/infrastructure/RecentlyViewedItems';

const SLASH_KEY = 191;

const isQuestionMark = ({ which, shiftKey }: KeyboardEvent) => which === SLASH_KEY && shiftKey;

export interface IGlobalSearchState {
  showSearchResults: boolean;
  showRecentItems: boolean;
  showMinLengthWarning: boolean;
  query: string;
  querying: boolean;
  categories: ISearchResultSet[];
}

@UIRouterContext
@BindAll()
export class GlobalSearch extends React.Component<{}, IGlobalSearchState> {

  private container: HTMLElement;
  private searchField: HTMLInputElement;
  private resultRefs: HTMLElement[][];

  constructor() {
    super();
    this.state = {
      showSearchResults: false,
      showRecentItems: false,
      showMinLengthWarning: false,
      query: '',
      querying: false,
      categories: []
    };
  }

  public componentDidMount() {
    window.addEventListener('keyup', this.handleWindowKeyup);
    window.addEventListener('click', this.handleWindowClick);
  }

  public componentWillUnmount() {
    window.addEventListener('keyup', this.handleWindowKeyup);
    window.addEventListener('click', this.handleWindowClick);
  }

  private handleWindowKeyup(event: KeyboardEvent) {
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
  }

  private handleWindowClick(event: MouseEvent) {
    if (!this.container.contains((event.target as Node))) {
      this.hideResults();
    }
  }

  private searchFieldBlurred({ relatedTarget }: React.FocusEvent<HTMLInputElement>) {
    if (!this.container.contains((relatedTarget as Node))) {
      this.hideResults();
    }
  }

  private searchFieldKeyUp(event: React.KeyboardEvent<HTMLInputElement>) {
    if (!this.state.showSearchResults && !this.state.showRecentItems) {
      return;
    }

    const { which, shiftKey } = event;

    if (which === 27) { // escape
      ReactGA.event({ category: 'Global Search', action: 'Keyboard Nav', label: 'escape (from input)' });
      return this.reset();
    }
    if (which === 40) { // down
      ReactGA.event({ category: 'Global Search', action: 'Keyboard Nav', label: 'arrow down (from input)' });
      event.preventDefault();
      return this.focusFirstSearchResult();
    }
    if (which === 38) { // up
      ReactGA.event({ category: 'Global Search', action: 'Keyboard Nav', label: 'arrow up (from input)' });
      event.preventDefault();
      return this.focusLastSearchResult();
    }
    if (which === 9) { // tab
      if (!shiftKey) {
        ReactGA.event({ category: 'Global Search', action: 'Keyboard Nav', label: 'tab (from input)' });
        event.preventDefault();
        this.focusFirstSearchResult();
      }
      return;
    }
  }

  private focusFirstSearchResult() {
    const refToFocus = this.resultRefs[0] && this.resultRefs[0][0];
    refToFocus && refToFocus.focus();
  }

  private focusLastSearchResult() {
    const flattenedRefs = flatten(this.resultRefs);
    const refToFocus = flattenedRefs[flattenedRefs.length - 1];
    refToFocus && refToFocus.focus();
  }

  private queryChanged({ target }: React.ChangeEvent<HTMLInputElement>) {
    this.setState({ query: target.value }, () => {
      if (this.state.query.length >= 3) {
        this.executeQuery();
      } else {
        this.considerMinLengthWarning();
      }
    });
  }

  private navigateResult(event: React.KeyboardEvent<HTMLAnchorElement>) {
    const { which, target } = event;
    if (which === 27) { // escape
      ReactGA.event({ category: 'Global Search', action: 'Keyboard Nav', label: 'escape (from result)' });
      this.reset();
    }
    if (which === 9) { // tab - let it navigate automatically, but close menu if on the last result
      const flattenedRefs = flatten(this.resultRefs);
      const lastResultRef = flattenedRefs[flattenedRefs.length - 1];
      if (target === lastResultRef) {
        ReactGA.event({ category: 'Global Search', action: 'Keyboard Nav', label: 'tab (from result)' });
        this.hideResults();
        return;
      }
    }
    if (which === 40) { // down
      ReactGA.event({ category: 'Global Search', action: 'Keyboard Nav', label: 'down (from result)' });
      const flattenedRefs = flatten(this.resultRefs);
      const currentRefIndex = flattenedRefs.indexOf((target as HTMLElement));
      const nextResultRef = flattenedRefs[currentRefIndex + 1];

      nextResultRef && nextResultRef.focus();
      event.preventDefault();
    }
    if (which === 38) { // up
      ReactGA.event({ category: 'Global Search', action: 'Keyboard Nav', label: 'up (from result)' });
      const flattenedRefs = flatten(this.resultRefs);
      const currentRefIndex = flattenedRefs.indexOf((target as HTMLElement));
      const prevResultRef = flattenedRefs[currentRefIndex - 1];

      prevResultRef && prevResultRef.focus();
      event.preventDefault();
    }
  }

  @Debounce(300)
  private executeQuery() {
    const { infrastructureSearchService } = ReactInjector;
    const search = infrastructureSearchService.getSearcher();

    ReactGA.event({ category: 'Global Search', action: 'Query', label: this.state.query });
    this.setState({ querying: true, showMinLengthWarning: false });

    search.query(this.state.query).then((result) => {
      const showMinLengthWarning = !!this.state.query && this.state.query.length < 3;

      const categories = result
        .filter(({ results }) => results.length)
        .map((category) => ({
          ...category,
          results: searchRank(category.results).slice(0, 5)
        }))
        .sort((a, b) => a.type.order - b.type.order);

      this.resultRefs = range(categories.length).map(() => []);

      this.setState({
        querying: false,
        showSearchResults: !!this.state.query && !showMinLengthWarning,
        categories,
        showMinLengthWarning
      });
    });
  }

  @Debounce(300)
  private considerMinLengthWarning() {
    const { query } = this.state;
    this.setState({ showMinLengthWarning: !!query && query.length < 3 });
  }

  private showResults() {
    const { query } = this.state;
    if (query) {
      const showMinLengthWarning = query.length < 3;
      this.setState({
        showSearchResults: !showMinLengthWarning,
        showRecentItems: false,
        showMinLengthWarning
      });
    } else {
      this.showRecentItems();
    }
  }

  private hideResults() {
    this.setState({
      showSearchResults: false,
      showRecentItems: false,
      showMinLengthWarning: false
    });
  }

  private reset() {
    this.setState({
      showSearchResults: false,
      showRecentItems: false,
      showMinLengthWarning: false,
      query: '',
      querying: false,
      categories: []
    });
  }

  private showRecentItems() {
    this.setState({ showRecentItems: true });
  }

  private clearFilters(result: ISearchResult) {
    ReactInjector.clusterFilterService.overrideFiltersForUrl(result);
  }

  public render() {
    const {
      showSearchResults,
      showRecentItems,
      showMinLengthWarning,
      query,
      querying,
      categories
    } = this.state;

    const { searchVersion } = SETTINGS;

    return (
      <li
        ref={(ref) => this.container = ref}
        className="global-search open"
      >
        <form className="right global-search">
          <div className="form-group has-feedback">
            <div className="input-group">
              <input
                ref={(ref) => this.searchField = ref}
                type="search"
                className="form-control flat input-sm no-border"
                placeholder="Search"
                value={query}
                onBlur={this.searchFieldBlurred}
                onFocus={this.showResults}
                onChange={this.queryChanged}
                onKeyUp={this.searchFieldKeyUp}
              />
              <Tooltip
                placement="right"
                template={
                  <span>Keyboard shortcut: <span className="keyboard-key">/</span></span>
                }
              >
                <i className="glyphicon glyphicon-search form-control-feedback" />
              </Tooltip>
            </div>
          </div>
        </form>
        {!!query && !querying && showMinLengthWarning &&
          <ul className="dropdown-menu" role="menu">
            <li className="horizontal middle center spinner-section">
              <span className="error-message">Please enter at least 3 characters</span>
            </li>
          </ul>
        }
        {!!query && querying &&
          <ul className="dropdown-menu" role="menu">
            <li className="horizontal middle center spinner-section">
              <Spinner size="small"/>
            </li>
          </ul>
        }
        {!query && showRecentItems &&
          <RecentlyViewedItems
            limit={5}
            Component={({ results: recentCategories }) => {
              if (!recentCategories.length) {
                return null;
              }

              this.resultRefs = range(recentCategories.length).map(() => []);

              return (
                <ul className="dropdown-menu" role="menu">
                  {recentCategories.map((category, categoryIndex) => ([
                    <li key={category.category} className="category-heading">
                      <div className="category-heading">Recent {category.category}</div>
                    </li>,
                    ...category.results.map((result, index) => {
                      const params = result.params || {};
                      const account = result.account || params.account || params.accountId || params.accountName;

                      return (
                        <li
                          key={result.id}
                          className="result"
                          onClick={() => {
                            this.hideResults();
                            ReactGA.event({ category: 'Global Search', action: 'Recent Item Selected' });
                          }}
                        >
                          <UISref to={result.state} params={result.params}>
                            <a
                              ref={(ref) => {
                                if (this.resultRefs[categoryIndex]) {
                                  this.resultRefs[categoryIndex][index] = ref;
                                }
                              }}
                              onKeyDown={this.navigateResult}
                            >
                              <SearchResult displayName={result.displayName} account={account} />
                            </a>
                          </UISref>
                        </li>
                      );
                    })
                  ]))}
                </ul>
              );
            }}
          />
        }
        {!!query && !querying && showSearchResults &&
          <ul className="dropdown-menu" role="menu">
            {categories.map((category, categoryIndex) => ([
              <li key={category.type.id} className="category-heading">
                <div className="category-heading">{category.type.displayName}</div>
              </li>,
              category.results.map((result, index) => (
                <li key={result.id} className="result">
                  <a
                    onKeyDown={this.navigateResult}
                    onClick={() => {
                      ReactGA.event({ category: 'Global Search', action: 'Result Selected' });
                      this.hideResults();
                      this.clearFilters(result);
                    }}
                    ref={(ref) => {
                      if (this.resultRefs[categoryIndex]) {
                        this.resultRefs[categoryIndex][index] = ref;
                      }
                    }}
                    // TODO: probably worth moving these (and the href for 'see more results') over to a UISRef at some point
                    href={result.href}
                  >
                    <SearchResult displayName={result.displayName} account={(result as any).account} />
                  </a>
                </li>
              ))
            ]))}
            {!!categories.length && [
              <li key="divider" className="divider"/>,
              <li key="seeMore" className="result">
                <a
                  href={searchVersion === 2 ? `#/search?key=${query}` : `#/infrastructure?q=${query}`}
                  className="expand-results"
                  onClick={() => {
                    ReactGA.event({ category: 'Global Search', action: 'See more results selected' });
                  }}
                >
                  See more results
                </a>
              </li>
            ]}
            {!categories.length &&
              <li className="result">
                <a>No matches</a>
              </li>
            }
          </ul>
        }
      </li>
    );
  }
}
