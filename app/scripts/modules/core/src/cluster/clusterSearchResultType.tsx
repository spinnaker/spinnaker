import * as React from 'react';
import { uniqBy } from 'lodash';
import { Observable } from 'rxjs';

import { IApplicationSummary } from 'core/application';
import { IQueryParams, urlBuilderRegistry } from 'core/navigation';
import { ReactInjector } from 'core/reactShims';
import { IServerGroupSearchResult } from 'core/serverGroup/serverGroupSearchResultType';

import {
  searchResultTypeRegistry,
  AccountCell,
  BasicCell,
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
  email?: string;
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
    EMAIL: { key: 'email' },
  };

  public TabComponent = DefaultSearchResultTab;

  public HeaderComponent = () => (
    <TableHeader>
      <HeaderCell col={this.cols.CLUSTER} />
      <HeaderCell col={this.cols.ACCOUNT} />
      <HeaderCell col={this.cols.EMAIL} />
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
            <BasicCell item={item} col={this.cols.EMAIL} />
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
    const applications: Map<string, IApplicationSummary> = ReactInjector.applicationReader.getApplicationMap();

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
        const clusterResults = clusters.map(cluster => {
          const app = applications.get(cluster.application);
          return { ...cluster, email: app && app.email };
        });

        return { results: clusterResults };
      });
  }

  public displayFormatter(searchResult: IClusterSearchResult) {
    return searchResult.cluster;
  }
}

searchResultTypeRegistry.register(new ClustersSearchResultType());
