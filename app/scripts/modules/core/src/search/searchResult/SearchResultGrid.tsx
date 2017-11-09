import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { ISearchResultType } from './searchResultsType.registry';
import { SearchStatus } from './SearchResults';
import { Spinner } from 'core/widgets';

export interface ISearchResultGridProps {
  searchStatus: SearchStatus;
  searchResultsType: ISearchResultType;
  searchResults: any[];
}

const NoQuery = () => (
  <div className="flex-fill vertical center middle">
    <h2>Please enter a search query to get started</h2>
  </div>
);

const NoResults = () => (
  <div className="flex-fill vertical center middle">
    <h2>No results found for the specified search query</h2>
  </div>
);

@BindAll()
export class SearchResultGrid extends React.Component<ISearchResultGridProps> {
  public render(): React.ReactElement<SearchResultGrid> {
    const { searchStatus, searchResults, searchResultsType } = this.props;

    switch (searchStatus) {
      case SearchStatus.INITIAL:
        return <NoQuery/>;
      case SearchStatus.SEARCHING:
        return (
          <div className="flex-fill vertical center middle">
            <Spinner size="large" message="Fetching search results ..."/>
          </div>
        );
      case SearchStatus.NO_RESULTS:
        return <NoResults/>;
      case SearchStatus.FINISHED:
        const { SearchResultsHeader, SearchResultsData } = searchResultsType.components;

        return (
          <div className="search-result-grid flex-fill" style={{ height: 'initial' }}>
            <div className={`table table-search-results table-search-results-${searchResultsType.id}`}>
              <SearchResultsHeader type={searchResultsType} />
              <SearchResultsData type={searchResultsType} results={searchResults} />
            </div>
          </div>
        );
      default:
        return null;
    }
  }
}
