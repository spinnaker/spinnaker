import { UISref } from '@uirouter/react';
import { UIRouterContext } from '@uirouter/react-hybrid';
import React from 'react';

import { ISearchResultSet } from '../infrastructure/infrastructureSearch.service';
import { SearchResultType } from './searchResultType';

export interface ISearchResultTabsProps {
  resultSets: ISearchResultSet[];
  activeSearchResultType: SearchResultType;
}

@UIRouterContext
export class SearchResultTabs extends React.Component<ISearchResultTabsProps> {
  public render(): React.ReactElement<SearchResultTabs> {
    const { activeSearchResultType } = this.props;
    const resultSets = this.props.resultSets.slice().sort((a, b) => a.type.order - b.type.order);

    return (
      <ul className="search-groups nostyle">
        {resultSets.map((resultSet) => {
          const { type } = resultSet;
          const { TabComponent } = type;
          const active = type === activeSearchResultType;

          return (
            <UISref key={type.id} to="." params={{ tab: type.id }}>
              <li>
                <TabComponent resultSet={resultSet} isActive={active} />
              </li>
            </UISref>
          );
        })}
      </ul>
    );
  }
}
