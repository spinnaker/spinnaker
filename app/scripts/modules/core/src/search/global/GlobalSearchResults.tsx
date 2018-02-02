import * as React from 'react';
import { UISref } from '@uirouter/react';

import { SETTINGS } from 'core/config/settings';
import { ISearchResult } from 'core/search/search.service';
import { ISearchResultSet } from 'core/search/infrastructure/infrastructureSearch.service';
import { SearchResult } from 'core/search/infrastructure/SearchResult';

export interface IGlobalSearchResultsProps {
  categories: ISearchResultSet[];
  query: string;
  onItemKeyDown: React.EventHandler<React.KeyboardEvent<HTMLAnchorElement>>;
  onResultClick: (result: ISearchResult) => any;
  onSeeMoreClick: React.EventHandler<React.MouseEvent<HTMLAnchorElement>>;
  resultRef: (categoryIndex: number, resultIndex: number, ref: HTMLAnchorElement) => any;
  seeMoreRef: (ref: HTMLAnchorElement) => any;
}

export const GlobalSearchResults = ({
  categories,
  query,
  onItemKeyDown,
  onResultClick,
  onSeeMoreClick,
  resultRef,
  seeMoreRef
}: IGlobalSearchResultsProps) => {
  const { searchVersion } = SETTINGS;

  if (!categories.length) {
    return (
      <ul className="dropdown-menu" role="menu">
        <li className="result">
          <a>No matches</a>
        </li>
      </ul>
    );
  }

  return (
    <ul className="dropdown-menu" role="menu">
      {categories.map((category, categoryIndex) => ([
        <li key={category.type.id} className="category-heading">
          <div className="category-heading">{category.type.displayName}</div>
        </li>,
        category.results.map((result, index) => (
          <li key={result.id} className="result">
            <a
              onKeyDown={onItemKeyDown}
              onClick={() => onResultClick(result)}
              ref={(ref) => resultRef(categoryIndex, index, ref)}
              // TODO: probably worth moving these (and the href for 'see more results') over to a UISRef at some point
              href={result.href}
            >
              <SearchResult displayName={result.displayName} account={(result as any).account} />
            </a>
          </li>
        ))
      ]))}
      <li key="divider" className="divider" />
      <li key="seeMore" className="result">
        <UISref
          to="home.search"
          // TODO: Setup route redirects to where this is unnecessary
          params={searchVersion === 2 ? { key: query } : { q: query }}
        >
          <a
            className="expand-results"
            ref={seeMoreRef}
            onKeyDown={onItemKeyDown}
            onClick={onSeeMoreClick}
          >
            See more results
          </a>
        </UISref>
      </li>
    </ul>
  );
}
