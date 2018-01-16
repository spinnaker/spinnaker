import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { ISearchResultSet } from '../infrastructure/infrastructureSearch.service';
import { ISearchResultType } from './searchResultsType.registry';
import { SearchResultGrid } from './SearchResultGrid';
import { SearchResultTabs } from './SearchResultTabs';

import './searchResults.less';

export enum SearchStatus {
  INITIAL, SEARCHING, FINISHED, NO_RESULTS
}

export interface ISearchResultData {
  type: ISearchResultType;
  results: any[];
}

export interface ISearchResultsProps {
  searchStatus: SearchStatus;
  searchResultTypes: ISearchResultType[];
  searchResultCategories: ISearchResultSet[];
  searchResultProjects: ISearchResultSet[];
}

export interface ISearchResultsState {
  active: ISearchResultType;
  searchResultData: ISearchResultData[];
}

@BindAll()
export class SearchResults extends React.Component<ISearchResultsProps, ISearchResultsState> {
  constructor(props: ISearchResultsProps) {
    super(props);
    this.state = { active: null, searchResultData: this.buildSearchResultData(props) };
  }

  public componentWillReceiveProps(newProps: ISearchResultsProps): void {
    const searchResultData: ISearchResultData[] = this.buildSearchResultData(newProps);
    // Update 'active' to first group with any results
    const hasResults: ISearchResultData = searchResultData.find(group => group.results.length > 0);
    this.setState({ searchResultData, active: hasResults && hasResults.type });
  }

  private handleClick(selectedSearchResultType: ISearchResultType): void {
    this.setState({ active: selectedSearchResultType });
  }

  private buildSearchResultData(props: ISearchResultsProps): ISearchResultData[] {
    const { searchResultTypes, searchResultProjects, searchResultCategories } = props;
    const searchResults = [...searchResultProjects, ...searchResultCategories];

    return searchResultTypes.map(type => {
      const resultForGroup: ISearchResultSet = searchResults.find(result => result.type === type);
      const results = (resultForGroup ? resultForGroup.results : []);
      return { type, results };
    });
  }

  public render() {
    const { searchStatus } = this.props;
    const { active, searchResultData } = this.state;
    const activeGroup = active && searchResultData.find(group => group.type === active);

    return (
      <div className="search-results">
        <SearchResultTabs
          searchResultData={searchResultData}
          activeSearchResultType={active}
          onClick={this.handleClick}
        />

        <SearchResultGrid
          searchStatus={searchStatus}
          searchResultsType={active}
          searchResults={activeGroup && activeGroup.results}
        />
      </div>
    );
  }
}
