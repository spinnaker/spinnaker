import { UISref } from '@uirouter/react';
import React from 'react';

import { SearchResult } from '../infrastructure/SearchResult';
import { ISearchResultPodData } from '../infrastructure/SearchResultPods';

export interface IGlobalSearchRecentItemsProps {
  categories: ISearchResultPodData[];
  onItemKeyDown: React.EventHandler<React.KeyboardEvent<HTMLAnchorElement>>;
  onResultClick: (category: string) => void;
  resultRef: (categoryIndex: number, resultIndex: number, ref: HTMLAnchorElement) => any;
}

export const GlobalSearchRecentItems = ({
  categories,
  onItemKeyDown,
  onResultClick,
  resultRef,
}: IGlobalSearchRecentItemsProps) => {
  if (!categories.length) {
    return null;
  }

  return (
    <ul className="dropdown-menu" role="menu">
      {categories.map((category, categoryIndex) => [
        <li key={category.category} className="category-heading">
          <div className="category-heading">Recent {category.category}</div>
        </li>,
        ...category.results.map((result, index) => {
          const params = result.params || {};
          const account = result.account || params.account || params.accountId || params.accountName;

          return (
            <li key={result.id} className="result" onClick={() => onResultClick(category.category)}>
              <UISref to={result.state} params={result.params}>
                <a ref={(ref) => resultRef(categoryIndex, index, ref)} onKeyDown={onItemKeyDown}>
                  <SearchResult displayName={result.displayName} account={account} />
                </a>
              </UISref>
            </li>
          );
        }),
      ])}
    </ul>
  );
};
