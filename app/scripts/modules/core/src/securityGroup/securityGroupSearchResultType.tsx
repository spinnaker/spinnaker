import * as React from 'react';

import {
  AccountCell, BasicCell, HrefCell, searchResultTypeRegistry, ISearchColumn, ISearchResultType,
  SearchResultsHeaderComponent, SearchResultsDataComponent, DefaultSearchResultTab,
  ISearchResult, HeaderCell, TableBody, TableHeader, TableRow,
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

const cols: { [key: string]: ISearchColumn } = {
  NAME: { key: 'name' },
  ACCOUNT: { key: 'account' },
  REGION: { key: 'region' }
};

const iconClass = 'fa fa-exchange';
const displayName = 'Security Groups';

const itemKeyFn = (item: ISecurityGroupSearchResult) =>
  [item.id, item.name, item.account, item.region].join('|');
const itemSortFn = (a: ISecurityGroupSearchResult, b: ISecurityGroupSearchResult) => {
  const order = a.name.localeCompare(b.name);
  return order !== 0 ? order : a.region.localeCompare(b.region);
};

const SearchResultsHeader: SearchResultsHeaderComponent = () => (
  <TableHeader>
    <HeaderCell col={cols.NAME}/>
    <HeaderCell col={cols.ACCOUNT}/>
    <HeaderCell col={cols.REGION}/>
  </TableHeader>
);

const SearchResultsData: SearchResultsDataComponent = ({ results }) => (
  <TableBody>
    {results.slice().sort(itemSortFn).map(item => (
      <TableRow key={itemKeyFn(item)}>
        <HrefCell item={item} col={cols.NAME} />
        <AccountCell item={item} col={cols.ACCOUNT} />
        <BasicCell item={item} col={cols.REGION} />
      </TableRow>
    ))}
  </TableBody>
);

const securityGroupsSearchResultType: ISearchResultType = {
  id: 'securityGroups',
  iconClass,
  displayName,
  order: 6,
  displayFormatter: (searchResult: ISecurityGroupSearchResult) => `${searchResult.name} (${searchResult.region})`,
  components: {
    SearchResultTab: DefaultSearchResultTab,
    SearchResultsHeader,
    SearchResultsData,
  },
};

searchResultTypeRegistry.register(securityGroupsSearchResultType);
