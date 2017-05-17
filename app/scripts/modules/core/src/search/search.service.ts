import {module, ILogService, IHttpPromiseCallbackArg, IPromise} from 'angular';
import {API_SERVICE, Api} from 'core/api/api.service';
import {ICache} from 'core/cache/deckCache.service';

export interface ISearchParams {
  pageSize?: number;
  q?: string;
  type?: string | string[];
  platform?: string;
  pageNumber?: number;
  filter?: {[key: string]: string};
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
  type: string;
  provider: string;
  displayName?: string;
  href?: string;
}

export const getFallbackResults = (): ISearchResults<ISearchResult> => {
  return { results: [] };
};

export class SearchService {

  static get defaultPageSize() { return 500; };

  constructor(private $log: ILogService, private API: Api) { 'ngInject'; }

  public search<T extends ISearchResult>(params: ISearchParams, cache: ICache = null): IPromise<ISearchResults<T>> {
    const defaultParams: ISearchParams = {
      pageSize: SearchService.defaultPageSize,
    };

    const requestBuilder = this.API.one('search').withParams(Object.assign(params, defaultParams));
    if (cache) {
      requestBuilder.useCache(cache);
    }
    return requestBuilder.get()
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
module(SEARCH_SERVICE, [API_SERVICE])
  .service('searchService', SearchService);
