import { FirewallLabels } from './label';
import React from 'react';

import {
  AccountCell,
  BasicCell,
  HrefCell,
  searchResultTypeRegistry,
  ISearchColumn,
  DefaultSearchResultTab,
  ISearchResult,
  HeaderCell,
  TableBody,
  TableHeader,
  TableRow,
  SearchResultType,
  ISearchResultSet,
} from 'core/search';

export interface ISecurityGroupSearchResult extends ISearchResult {
  account: string;
  application: string;
  href: string;
  id: string;
  name: string;
  provider: string;
  region: string;
  type: string;
  url: string;
  vpcId: string;
}

class SecurityGroupsSearchResultType extends SearchResultType<ISecurityGroupSearchResult> {
  public id = 'securityGroups';
  public order = 6;
  public displayName = FirewallLabels.get('Firewalls');
  public iconClass = 'fa fa-exchange-alt';

  private cols: { [key: string]: ISearchColumn } = {
    NAME: { key: 'name' },
    ACCOUNT: { key: 'account' },
    REGION: { key: 'region' },
  };

  public TabComponent = DefaultSearchResultTab;

  public HeaderComponent = () => (
    <TableHeader>
      <HeaderCell col={this.cols.NAME} />
      <HeaderCell col={this.cols.ACCOUNT} />
      <HeaderCell col={this.cols.REGION} />
    </TableHeader>
  );

  public DataComponent = ({ resultSet }: { resultSet: ISearchResultSet<ISecurityGroupSearchResult> }) => {
    const itemKeyFn = (item: ISecurityGroupSearchResult) => [item.id, item.name, item.account, item.region].join('|');
    const itemSortFn = (a: ISecurityGroupSearchResult, b: ISecurityGroupSearchResult) => {
      const order = a.name.localeCompare(b.name);
      return order !== 0 ? order : a.region.localeCompare(b.region);
    };

    const results = resultSet.results.slice().sort(itemSortFn);

    return (
      <TableBody>
        {results
          .slice()
          .sort(itemSortFn)
          .map(item => (
            <TableRow key={itemKeyFn(item)}>
              <HrefCell item={item} col={this.cols.NAME} />
              <AccountCell item={item} col={this.cols.ACCOUNT} />
              <BasicCell item={item} col={this.cols.REGION} />
            </TableRow>
          ))}
      </TableBody>
    );
  };

  public displayFormatter(searchResult: ISecurityGroupSearchResult) {
    return `${searchResult.name} (${searchResult.region})`;
  }
}

searchResultTypeRegistry.register(new SecurityGroupsSearchResultType());
