import { IHttpPromiseCallbackArg, IPromise } from 'angular';
import { $log } from 'ngimport';

import { API } from 'core/api/ApiService';
import { ICache } from 'core/cache';

export interface ISearchParams {
  [key: string]: any;
  q?: string;
  type?: string | string[];
  platform?: string;
  pageNumber?: number;
  pageSize?: number;
  allowShortQuery?: 'true' | undefined;
}

export interface ISearchResults<T extends ISearchResult> {
  results: T[];
  pageNumber?: number;
  pageSize?: number;
  platform?: string;
  query?: string;
  totalMatches?: number;
}

export interface ISearchResult {
  displayName?: string;
  href?: string;
  provider: string;
  type: string;
}

export const getFallbackResults = (): ISearchResults<ISearchResult> => {
  return { results: [] };
};

export class SearchService {
  static get DEFAULT_PAGE_SIZE(): number {
    return 500;
  }

  public static search<T extends ISearchResult>(
    searchParams: ISearchParams,
    cache: ICache = null,
  ): IPromise<ISearchResults<T>> {
    const defaultParams: ISearchParams = {
      pageSize: SearchService.DEFAULT_PAGE_SIZE,
    };

    const params = { ...searchParams, ...defaultParams };

    const requestBuilder = API.one('search').withParams(params);

    if (cache) {
      requestBuilder.useCache(cache);
    }

    return requestBuilder
      .get()
      .then((response: Array<ISearchResults<T>>) => {
        return response[0] || getFallbackResults();
      })
      .catch((response: IHttpPromiseCallbackArg<any>) => {
        $log.error(response.data, response);
        return getFallbackResults();
      });
  }
}
