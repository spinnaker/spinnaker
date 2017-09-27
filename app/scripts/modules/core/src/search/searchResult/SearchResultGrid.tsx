import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { ISearchResultFormatter } from './searchResultFormatter.registry';
import { SearchStatus } from './SearchResults';

export interface ISearchResultGridProps {
  searchStatus: SearchStatus;
  searchResultFormatter: ISearchResultFormatter;
  searchResults: any[];
}

@BindAll()
export class SearchResultGrid extends React.Component<ISearchResultGridProps> {

  public componentDidUpdate(): void {
    const { searchResultFormatter } = this.props;
    if (searchResultFormatter) {
      searchResultFormatter.displayRenderer.scrollToTop();
    }
  }

  public render(): React.ReactElement<SearchResultGrid> {

    const { searchStatus, searchResultFormatter, searchResults } = this.props;
    switch (searchStatus) {
      case SearchStatus.INITIAL:
        return (
          <div className="flex-center">
            <h2>Please enter a search query to get started</h2>
          </div>
        );
      case SearchStatus.SEARCHING:
        return (
          <div className="load large flex-center">
            <div className="message">Fetching search results...</div>
            <div className="bars">
              <div className="bar full"/>
              <div className="bar"/>
              <div className="bar"/>
              <div className="bar"/>
              <div className="bar"/>
            </div>
          </div>
        );
      case SearchStatus.NO_RESULTS:
        return (
          <div className="flex-center">
            <h2>No results found for the specified search query</h2>
          </div>
        );
      case SearchStatus.FINISHED:
        return (
          <div className="search-result-grid flex-fill" style={{ height: 'initial' }}>
            {searchResultFormatter.displayRenderer.render(searchResults)}
          </div>
        );
      default:
        return null;
    }
  }
}
