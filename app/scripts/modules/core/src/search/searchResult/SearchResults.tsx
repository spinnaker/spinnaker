import * as React from 'react';

import { ISearchResultSet } from '../infrastructure/infrastructureSearch.service';
import { SearchResultType } from './searchResultType';
import { Searching, SearchResultGrid } from './SearchResultGrid';
import { SearchResultTabs } from './SearchResultTabs';

import './searchResults.less';

export enum SearchStatus {
  INITIAL, SEARCHING, FINISHED, NO_RESULTS, ERROR
}

export interface ISearchResultsProps {
  selectedTab: string;
  resultSets: ISearchResultSet[];
  isSearching: boolean;
}

export interface ISearchResultsState {
  active: SearchResultType;
}

export class SearchResults extends React.Component<ISearchResultsProps, ISearchResultsState> {
  public state = { active: null as any };

  public componentWillReceiveProps(newProps: ISearchResultsProps): void {
    const { resultSets, selectedTab } = newProps;
    const active: SearchResultType = resultSets.map(x => x.type).find(type => type.id === selectedTab);
    this.setState({ active });
  }

  public render() {
    const { resultSets, isSearching } = this.props;
    const { active } = this.state;
    const activeResultSet = active && resultSets.find(resultSet => resultSet.type === active);

    return (
      <div className="search-results">
        <SearchResultTabs resultSets={resultSets} activeSearchResultType={active} />
        {activeResultSet && <SearchResultGrid resultSet={activeResultSet} />}
        {!activeResultSet && isSearching && <Searching />}
      </div>
    );
  }
}
