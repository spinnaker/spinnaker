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

export interface ISearchResults<T> {
  results: T[];
  pageNumber?: number;
  pageSize?: number;
  platform?: string;
  query?: string;
  totalMatches?: number;
}

export class SearchService {

  static get defaultPageSize() { return 500; };

  static get $inject() { return ['$log', 'API']; }

  constructor(private $log: ILogService, private API: Api) { }

  public search<T>(params: ISearchParams, cache: ICache = null): IPromise<ISearchResults<T>> {
    const defaultParams: ISearchParams = {
      pageSize: SearchService.defaultPageSize,
    };

    let requestBuilder = this.API.one('search').withParams(Object.assign(params, defaultParams));
    if (cache) {
      requestBuilder.useCache(cache);
    }
    return requestBuilder.get()
      .then((response: ISearchResults<T>[]) => {
        return response[0] || this.getFallbackResults();
      })
      .catch((response: IHttpPromiseCallbackArg<any>) => {
        this.$log.error(response.data, response);
        return this.getFallbackResults();
      });
  }

  public getFallbackResults(): ISearchResults<any> {
    return { results: [] };
  }
}

export const SEARCH_SERVICE = 'spinnaker.core.search.service';
module(SEARCH_SERVICE, [API_SERVICE])
  .service('searchService', SearchService);
