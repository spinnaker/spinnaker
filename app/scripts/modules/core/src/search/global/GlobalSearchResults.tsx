import { UISref } from '@uirouter/react';
import React from 'react';

import { SearchResult } from '../infrastructure/SearchResult';
import { ISearchResultSet } from '../infrastructure/infrastructureSearch.service';
import { ISearchResult } from '../search.service';
import { findMatchingApplicationResultToQuery, getSearchQuery } from './utils';

export interface IGlobalSearchResultsProps {
  categories: ISearchResultSet[];
  query: string;
  onItemKeyDown: React.EventHandler<React.KeyboardEvent<HTMLAnchorElement>>;
  onResultClick: (result: ISearchResult) => any;
  onSeeMoreClick: () => any;
  resultRef: (categoryIndex: number, resultIndex: number, ref: HTMLAnchorElement) => any;
}

export const GlobalSearchResults = ({
  categories,
  query,
  onItemKeyDown,
  onResultClick,
  onSeeMoreClick,
  resultRef,
}: IGlobalSearchResultsProps) => {
  if (!categories.length) {
    return (
      <ul className="dropdown-menu" role="menu">
        <li className="result">
          <a>No matches</a>
        </li>
      </ul>
    );
  }

  const matchingApp = findMatchingApplicationResultToQuery(categories, query);

  return (
    <ul className="dropdown-menu" role="menu">
      {categories.map((category, categoryIndex) => {
        // TODO: Setup route redirects to where this is unnecessary
        const showMoreParams = getSearchQuery(query, category.type.id);

        return [
          <li key={category.type.id} className="category-heading flex-container-h no-wrap space-between baseline">
            <span>{category.type.displayName}</span>
            <UISref to="home.search" params={showMoreParams}>
              <a
                target="_self"
                className="expand-results"
                ref={(ref) => resultRef(categoryIndex, 0, ref)}
                onClick={onSeeMoreClick}
                onKeyDown={onItemKeyDown}
              >
                show all
              </a>
            </UISref>
          </li>,

          category.results.map((result, index) => (
            <li key={result.href} className="result">
              <a
                onKeyDown={onItemKeyDown}
                onClick={() => onResultClick(result)}
                ref={(ref) => resultRef(categoryIndex, index + 1, ref)}
                href={result.href}
                className={result === matchingApp?.result ? 'query-match' : undefined}
              >
                <SearchResult displayName={result.displayName} account={(result as any).account} />
              </a>
            </li>
          )),
        ];
      })}
    </ul>
  );
};
