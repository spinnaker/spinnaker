import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { ISearchResultData } from 'core/search/searchResult/SearchResults';
import { ISearchResultType } from './searchResultsType.registry';

export interface ISearchResultTabsProps {
  searchResultData: ISearchResultData[]
  activeSearchResultType: ISearchResultType;
  onClick?: (group: ISearchResultType) => void;
}

@BindAll()
export class SearchResultTabs extends React.Component<ISearchResultTabsProps> {
  private handleClick(type: ISearchResultType) {
    this.props.onClick && this.props.onClick(type);
  }

  public render(): React.ReactElement<SearchResultTabs> {
    const { searchResultData, activeSearchResultType } = this.props;

    return (
      <div className="search-groups">
        {searchResultData.map(({ type, results }) => {
          const { SearchResultTab } = type.components;
          return (
            <SearchResultTab
              key={type.id}
              type={type}
              resultsCount={results.length}
              isActive={type === activeSearchResultType}
              onClick={this.handleClick}
            />
          );
        })}
      </div>
    );
  }
}
