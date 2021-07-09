import React from 'react';

import {
  AccountCell,
  BasicCell,
  DefaultSearchResultTab,
  HeaderCell,
  HrefCell,
  ISearchColumn,
  ISearchResult,
  ISearchResultSet,
  SearchResultType,
  searchResultTypeRegistry,
  SearchTableBody,
  SearchTableHeader,
  SearchTableRow,
} from '../search';

export interface IApplicationSearchResult extends ISearchResult {
  accounts: string[];
  application: string;
  cloudProviders: string;
  createTs: string;
  description: string;
  email: string;
  group: string;
  lastModifiedBy: string;
  legacyUdf: boolean;
  name: string;
  owner: string;
  pdApiKey: string;
  updateTs: string;
  url: string;
  user: string;
}

class ApplicationSearchResultType extends SearchResultType<IApplicationSearchResult> {
  public id = 'applications';
  public order = 1;
  public displayName = 'Applications';
  public iconClass = 'far fa-window-maximize';

  private cols: { [key: string]: ISearchColumn } = {
    APPLICATION: { key: 'application', label: 'Name' },
    ACCOUNT: { key: 'accounts', label: 'Account' },
    EMAIL: { key: 'email' },
  };

  public TabComponent = DefaultSearchResultTab;

  public HeaderComponent = () => (
    <SearchTableHeader>
      <HeaderCell col={this.cols.APPLICATION} />
      <HeaderCell col={this.cols.ACCOUNT} />
      <HeaderCell col={this.cols.EMAIL} />
    </SearchTableHeader>
  );

  public DataComponent = ({ resultSet }: { resultSet: ISearchResultSet<IApplicationSearchResult> }) => {
    const itemKeyFn = (item: IApplicationSearchResult) => item.application;
    const itemSortFn = (a: IApplicationSearchResult, b: IApplicationSearchResult) =>
      a.application.localeCompare(b.application);
    const results = resultSet.results.slice().sort(itemSortFn);

    return (
      <SearchTableBody>
        {results.map((item) => (
          <SearchTableRow key={itemKeyFn(item)}>
            <HrefCell item={item} col={this.cols.APPLICATION} />
            <AccountCell item={item} col={this.cols.ACCOUNT} />
            <BasicCell item={item} col={this.cols.EMAIL} />
          </SearchTableRow>
        ))}
      </SearchTableBody>
    );
  };

  public displayFormatter(searchResult: IApplicationSearchResult) {
    return searchResult.application;
  }
}

searchResultTypeRegistry.register(new ApplicationSearchResultType());
