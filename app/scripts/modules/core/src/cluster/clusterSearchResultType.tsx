import * as React from 'react';
import { uniqBy } from 'lodash';
import { Observable } from 'rxjs';

import { IQueryParams, urlBuilderRegistry } from 'core/navigation';
import { ReactInjector } from 'core/reactShims';
import { IServerGroupSearchResult } from 'core/serverGroup/serverGroupSearchResultType';

import {
  searchResultTypeRegistry,
  AccountCell,
  HrefCell,
  ISearchColumn,
  DefaultSearchResultTab,
  SearchStatus,
  HeaderCell,
  TableBody,
  TableHeader,
  TableRow,
  ISearchResult,
  SearchResultType,
  ISearchResultSet,
  ISearchResults,
} from 'core/search';

export interface IClusterSearchResult extends ISearchResult {
  account: string;
  application: string;
  cluster: string;
  stack: string;
}

class ClustersSearchResultType extends SearchResultType<IClusterSearchResult> {
  public id = 'clusters';
  public order = 2;
  public displayName = 'Clusters';
  public iconClass = 'fa fa-th';

  private cols: { [key: string]: ISearchColumn } = {
    CLUSTER: { key: 'cluster', label: 'Name' },
    ACCOUNT: { key: 'account' },
  };

  public TabComponent = DefaultSearchResultTab;

  public HeaderComponent = () => (
    <TableHeader>
      <HeaderCell col={this.cols.CLUSTER} />
      <HeaderCell col={this.cols.ACCOUNT} />
    </TableHeader>
  );

  public DataComponent = ({ resultSet }: { resultSet: ISearchResultSet<IClusterSearchResult> }) => {
    const itemKeyFn = (item: IClusterSearchResult) => `${item.account}-${item.cluster}`;
    const itemSortFn = (a: IClusterSearchResult, b: IClusterSearchResult) => a.cluster.localeCompare(b.cluster);
    const results = resultSet.results.slice().sort(itemSortFn);

    return (
      <TableBody>
        {results.map(item => (
          <TableRow key={itemKeyFn(item)}>
            <HrefCell item={item} col={this.cols.CLUSTER} />
            <AccountCell item={item} col={this.cols.ACCOUNT} />
          </TableRow>
        ))}
      </TableBody>
    );
  };

  private makeSearchResult(serverGroup: IServerGroupSearchResult): IClusterSearchResult {
    const type = this.id;
    const urlBuilder = urlBuilderRegistry.getBuilder(type);
    const input = { type, ...serverGroup };
    const href = urlBuilder.build(input, ReactInjector.$state);

    const { account, application, cluster, provider, stack } = serverGroup;
    return { account, application, cluster, provider, stack, displayName: cluster, href, type };
  }

  // create cluster search results based on the server group search results
  public search(
    _params: IQueryParams,
    otherResults: Observable<ISearchResultSet>,
  ): Observable<ISearchResults<IClusterSearchResult>> {
    return otherResults
      .filter(resultSet => resultSet.type.id === 'serverGroups')
      .first()
      .map((resultSet: ISearchResultSet) => {
        const { status, results, error } = resultSet;
        if (status === SearchStatus.ERROR) {
          throw error;
        }

        const serverGroups = results as IServerGroupSearchResult[];
        const searchResults = serverGroups.map(sg => this.makeSearchResult(sg));
        const clusters: IClusterSearchResult[] = uniqBy(searchResults, sg => `${sg.account}-${sg.cluster}`);
        return { results: clusters };
      });
  }

  public displayFormatter(searchResult: IClusterSearchResult) {
    return searchResult.cluster;
  }
}

searchResultTypeRegistry.register(new ClustersSearchResultType());
