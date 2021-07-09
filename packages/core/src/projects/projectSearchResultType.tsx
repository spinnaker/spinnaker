import React from 'react';

import { IProjectConfig } from '../domain';
import {
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

export interface IProjectSearchResult extends ISearchResult {
  applications: string[];
  clusters: string[];
  config: IProjectConfig;
  createTs: number;
  displayName: string;
  email: string;
  href: string;
  id: string;
  lastModifiedBy: string;
  name?: string;
  pipelineConfigId: string;
  project?: string;
  type: string;
  updateTs: number;
  url: string;
}

class ProjectsSearchResultType extends SearchResultType<IProjectSearchResult> {
  public id = 'projects';
  public order = 0;
  public displayName = 'Projects';
  public iconClass = 'far fa-folder';

  private cols: { [key: string]: ISearchColumn } = {
    NAME: { key: 'name' },
    EMAIL: { key: 'email' },
  };

  public TabComponent = DefaultSearchResultTab;

  public HeaderComponent = () => (
    <SearchTableHeader>
      <HeaderCell col={this.cols.NAME} />
      <HeaderCell col={this.cols.EMAIL} />
    </SearchTableHeader>
  );

  public DataComponent = ({ resultSet }: { resultSet: ISearchResultSet<IProjectSearchResult> }) => {
    const itemKeyFn = (item: IProjectSearchResult) => item.id;
    const itemSortFn = (a: IProjectSearchResult, b: IProjectSearchResult) => a.name.localeCompare(b.name);

    const results = resultSet.results.slice().sort(itemSortFn);

    return (
      <SearchTableBody>
        {results
          .slice()
          .sort(itemSortFn)
          .map((item) => (
            <SearchTableRow key={itemKeyFn(item)}>
              <HrefCell item={item} col={this.cols.NAME} />
              <BasicCell item={item} col={this.cols.EMAIL} />
            </SearchTableRow>
          ))}
      </SearchTableBody>
    );
  };

  public displayFormatter(searchResult: IProjectSearchResult) {
    const applications =
      searchResult.config && searchResult.config.applications
        ? ` (${searchResult.config.applications.join(', ')})`
        : '';
    const project = searchResult.name || searchResult.project;
    return project + applications;
  }
}

searchResultTypeRegistry.register(new ProjectsSearchResultType());
