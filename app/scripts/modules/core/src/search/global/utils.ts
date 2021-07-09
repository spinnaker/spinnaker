import { IApplicationSearchResult } from '../../application/applicationSearchResultType';
import { SETTINGS } from '../../config/settings';

import { ISearchResultSet } from '../infrastructure/infrastructureSearch.service';

export const findMatchingApplicationResultToQuery = (categories: ISearchResultSet[] | undefined, query: string) => {
  if (!categories) {
    return undefined;
  }
  for (const category of categories) {
    if (category.type.id === 'applications') {
      const matchingApp = (category.results as IApplicationSearchResult[]).find(
        (result) => result.application.toLowerCase() === query.toLowerCase(),
      );
      if (matchingApp) {
        return { category: category, result: matchingApp };
      }
    }
  }
  return undefined;
};

export const getSearchQuery = (query: string, tab?: string) => {
  return SETTINGS.searchVersion === 2 ? { key: query, tab } : { q: query };
};
