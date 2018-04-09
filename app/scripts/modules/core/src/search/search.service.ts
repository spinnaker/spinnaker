import { module, ILogService, IHttpPromiseCallbackArg, IPromise } from 'angular';
import { API_SERVICE, Api } from 'core/api/api.service';
import { ICache } from 'core/cache/deckCache.service';

export interface ISearchParams {
  [key: string]: any;
  q?: string;
  type?: string | string[];
  platform?: string;
  pageNumber?: number;
  pageSize?: number;
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

  constructor(private $log: ILogService, private API: Api) {
    'ngInject';
  }

  public search<T extends ISearchResult>(
    searchParams: ISearchParams,
    cache: ICache = null,
  ): IPromise<ISearchResults<T>> {
    const defaultParams: ISearchParams = {
      pageSize: SearchService.DEFAULT_PAGE_SIZE,
    };

    const params = Object.assign(searchParams, defaultParams);

    const requestBuilder = this.API.one('search').withParams(params);

    if (cache) {
      requestBuilder.useCache(cache);
    }

    return requestBuilder
      .get()
      .then((response: ISearchResults<T>[]) => {
        return response[0] || getFallbackResults();
      })
      .catch((response: IHttpPromiseCallbackArg<any>) => {
        this.$log.error(response.data, response);
        return getFallbackResults();
      });
  }
}

export const SEARCH_SERVICE = 'spinnaker.core.search.service';
module(SEARCH_SERVICE, [API_SERVICE]).service('searchService', SearchService);
