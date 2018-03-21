import * as React from 'react';

import {
  searchResultTypeRegistry, BasicCell, HrefCell, ISearchResult, HeaderCell, DefaultSearchResultTab,
  TableBody, TableHeader, TableRow, ISearchColumn, SearchResultType, ISearchResultSet,
} from 'core/search';
import { IProjectConfig } from 'core/domain';

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
    <TableHeader>
      <HeaderCell col={this.cols.NAME}/>
      <HeaderCell col={this.cols.EMAIL}/>
    </TableHeader>
  );

  public DataComponent = ({ resultSet }: { resultSet: ISearchResultSet<IProjectSearchResult> }) => {
    const itemKeyFn = (item: IProjectSearchResult) => item.id;
    const itemSortFn = (a: IProjectSearchResult, b: IProjectSearchResult) =>
      a.name.localeCompare(b.name);

    const results = resultSet.results.slice().sort(itemSortFn);

    return (
      <TableBody>
        {results.slice().sort(itemSortFn).map(item => (
          <TableRow key={itemKeyFn(item)}>
            <HrefCell item={item} col={this.cols.NAME} />
            <BasicCell item={item} col={this.cols.EMAIL} />
          </TableRow>
        ))}
      </TableBody>
    );
  };

  public displayFormatter(searchResult: IProjectSearchResult) {
    const applications = searchResult.config && searchResult.config.applications ?
      ` (${searchResult.config.applications.join(', ')})` :
      '';
    const project = searchResult.name || searchResult.project;
    return project + applications;
  }
}

searchResultTypeRegistry.register(new ProjectsSearchResultType());
