import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';

import { ISearchResultFormatter, searchResultFormatterRegistry } from './searchResultFormatter.registry';
import { SearchResultGrid } from './SearchResultGrid';
import { SearchResultGroups } from './SearchResultGroups';
import { ISearchResultGroup } from './SearchResultGroup';

import './searchResults.less';

export enum SearchStatus {
  INITIAL, SEARCHING, FINISHED, NO_RESULTS
}

export interface ISearchResults {
  category: string;
  icon: string;
  iconClass: string;
  id: string;
  order: number;
  results: any[];
}

export interface ISearchResultsProps {
  searchStatus: SearchStatus;
  searchResultCategories: ISearchResults[];
  searchResultProjects: ISearchResults[];
}

export interface ISearchResultsState {
  active: ISearchResultGroup;
  searchResultGroups: ISearchResultGroup[];
  formatter: ISearchResultFormatter;
}

@autoBindMethods
export class SearchResults extends React.Component<ISearchResultsProps, ISearchResultsState> {

  private EMPTY_RESULT: ISearchResultGroup = Object.freeze({
    category: '',
    count: 0,
    iconClass: '',
    name: '',
    order: 0,
    results: []
  });

  constructor(props: ISearchResultsProps) {
    super(props);
    this.state = {
      active: this.EMPTY_RESULT,
      searchResultGroups: this.buildSearchResultGroups(),
      formatter: null
    };
  }

  private buildSearchResultGroups(): ISearchResultGroup[] {
    return searchResultFormatterRegistry.getSearchCategories()
      .sort((a, b) => searchResultFormatterRegistry.get(a).order - searchResultFormatterRegistry.get(b).order)
      .map((category: string) => {
        const formatter: ISearchResultFormatter = searchResultFormatterRegistry.get(category);
        return {
          category: category,
          count: 0,
          iconClass: formatter.icon ? `fa fa-${formatter.icon}` : formatter.iconClass,
          name: formatter.displayName,
          order: formatter.order,
          results: []
        };
      });
  }

  private handleClick(group: ISearchResultGroup): void {
    this.setState({
      active: group,
      formatter: searchResultFormatterRegistry.get(group.category)
    });
  }

  private transformSearchResults(searchResults: ISearchResults[]): void {

    this.state.searchResultGroups.forEach((group: ISearchResultGroup) => {
      const searchResult: ISearchResults =
        searchResults.find((result: ISearchResults) => group.category === (result.id || result.category));
      group.count = searchResult ? searchResult.results.length : 0;
      group.results = searchResult ? searchResult.results : []
    });
  }

  public componentWillReceiveProps(newProps: ISearchResultsProps): void {

    this.transformSearchResults([...newProps.searchResultProjects, ...newProps.searchResultCategories]);
    const active: ISearchResultGroup =
      this.state.searchResultGroups.find((group: ISearchResultGroup) => group.count > 0);
    this.setState({
      active,
      formatter: active ? searchResultFormatterRegistry.get(active.category) : undefined
    });
  }

  public render(): React.ReactElement<SearchResults> {

    const { searchStatus } = this.props;
    const { active, formatter, searchResultGroups } = this.state;
    return (
      <div className="search-results">
        <SearchResultGroups
          activeSearchResult={active}
          searchResultGroups={searchResultGroups}
          onClick={this.handleClick}
        />
        <SearchResultGrid
          searchStatus={searchStatus}
          searchResultFormatter={formatter}
          searchResults={active ? active.results : []}
        />
      </div>
    );
  }
}
