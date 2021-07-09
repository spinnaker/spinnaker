import { module } from 'angular';

import { ISearchResult } from './search.service';

export const searchRank = (input: ISearchResult[], query = '') => {
  if (!input || !input.length) {
    return [];
  }

  const normalizedQuery = query.toLowerCase();

  return input.slice().sort((a, b) => {
    if (a.displayName && b.displayName) {
      const aIndex = a.displayName.toLowerCase().indexOf(normalizedQuery);
      const bIndex = b.displayName.toLowerCase().indexOf(normalizedQuery);
      return aIndex === bIndex ? a.displayName.localeCompare(b.displayName) : aIndex - bIndex;
    }
    return 0;
  });
};

export const searchRankFilter = () => searchRank;

export const SEARCH_RANK_FILTER = 'spinnaker.core.search.searchResult.searchRank.filter';
module(SEARCH_RANK_FILTER, []).filter('searchRank', searchRankFilter);
