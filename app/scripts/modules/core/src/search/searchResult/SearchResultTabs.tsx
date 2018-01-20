import * as React from 'react';
import { UISref } from '@uirouter/react';
import { UIRouterContext } from '@uirouter/react-hybrid';

import { ISearchResultType } from './searchResultsType.registry';
import { ISearchResultSet } from '../infrastructure/infrastructureSearch.service';

export interface ISearchResultTabsProps {
  resultSets: ISearchResultSet[]
  activeSearchResultType: ISearchResultType;
}

@UIRouterContext
export class SearchResultTabs extends React.Component<ISearchResultTabsProps> {
  public render(): React.ReactElement<SearchResultTabs> {
    const { activeSearchResultType } = this.props;
    const resultSets = this.props.resultSets.slice().sort((a, b) => a.type.order - b.type.order);

    return (
      <ul className="search-groups nostyle">
        {resultSets.map(resultSet => {
          const { type } = resultSet;
          const { SearchResultTab } = type.components;
          const active = type === activeSearchResultType;

          return (
            <UISref key={type.id} to="." params={{ tab: type.id }}>
              <li><SearchResultTab resultSet={resultSet} isActive={active} /></li>
            </UISref>
          );
        })}
      </ul>
    );
  }
}
