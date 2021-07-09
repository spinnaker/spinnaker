import { IHttpPromiseCallbackArg } from 'angular';
import { $log } from 'ngimport';

import { API } from '../api/ApiService';
import { ICache } from '../cache';

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

const getFallbackResults = <T extends ISearchResult>(): ISearchResults<T> => {
  return { results: [] };
};

export class SearchService {
  static get DEFAULT_PAGE_SIZE(): number {
    return 500;
  }

  public static search<T extends ISearchResult>(
    searchParams: ISearchParams,
    cache: ICache = null,
  ): PromiseLike<ISearchResults<T>> {
    const defaultParams: ISearchParams = {
      pageSize: SearchService.DEFAULT_PAGE_SIZE,
    };

    const params = { ...searchParams, ...defaultParams };

    // eslint-disable-next-line @spinnaker/api-deprecation
    let requestBuilder = API.one('search').query(params);

    if (cache) {
      // TODO: This is the only usage of ICache in deck, investigate how we can avoid this and migrate to REST()
      requestBuilder = requestBuilder.useCache(cache);
    }

    return requestBuilder
      .get()
      .then((response: Array<ISearchResults<T>>) => {
        return response[0] || getFallbackResults<T>();
      })
      .catch((response: IHttpPromiseCallbackArg<any>) => {
        $log.error(response.data, response);
        return getFallbackResults();
      });
  }
}
