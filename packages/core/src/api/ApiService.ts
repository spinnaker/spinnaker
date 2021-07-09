import { isNil } from 'lodash';
import { $http } from 'ngimport';

import { AuthenticationInitializer } from '../authentication/AuthenticationInitializer';
import { ICache } from '../cache/deckCacheFactory';
import { SETTINGS } from '../config/settings';

type IPrimitive = string | boolean | number;
type IParams = Record<string, IPrimitive | IPrimitive[]>;

interface Headers {
  [headerName: string]: string;
}

/**
 * A Builder API for making requests to Gate backend service
 */
export interface IRequestBuilder {
  /**
   * Appends one or more path segments to the URL, separated by slashes.
   * Each path segment is uri encoded.
   */
  path(...pathSegments: IPrimitive[]): this;

  headers(headers: Headers): this;

  /** Adds query parameters to the URL */
  query(queryParams: IParams): this;

  /** Enables or disables caching of the response */
  useCache(useCache?: boolean): this;

  /** issues a GET request */
  get<T = any>(): PromiseLike<T>;
  /** issues a POST request */
  post<T = any, P = any>(data?: P): PromiseLike<T>;
  /** issues a PUT request */
  put<T = any, P = any>(data?: P): PromiseLike<T>;
  /** issues a PATCH request */
  patch<T = any, P = any>(data?: P): PromiseLike<T>;
  /** issues a DELETE request */
  delete<T = any, P = any>(data?: P): PromiseLike<T>;
}

/**
 * Internal interface to encapsulate a request
 * Passed to the IHttpClientImplementation
 */
interface IRequestBuilderConfig {
  url: string;
  timeout?: number;
  headers?: Headers;
  /** @deprecated used for AngularJS backwards compat */
  data?: any;
  params?: object;
  cache?: boolean;
}

/**
 * The old API interface
 */
export interface IDeprecatedRequestBuilder extends IRequestBuilder {
  useCache(): this;
  useCache(useCache: boolean): this;
  useCache(useCache: ICache): this;
  withParams(queryParams: IParams): this;

  /** @deprecated do not use this config object */
  config: IRequestBuilderConfig;
  /** @deprecated use SETTINGS.gateUrl */
  baseUrl: string;
  /** @deprecated use path() instead (this is a passthrough to path) */
  one(...urls: string[]): this;
  /** @deprecated use one() instead (this is a passthrough to one) */
  all(...urls: string[]): this;
  /** @deprecated use put(data) or post(data) instead */
  data(data: any): this;
  // Add overload with params
  get<T = any>(params?: IParams): PromiseLike<T>;
  /** @deprecated use delete() instead (this is a passthrough to delete) */
  remove(params?: IParams): PromiseLike<any>;
  /** @deprecated use get() instead (this is a passthrough to get) */
  getList<T = any>(params?: IParams): PromiseLike<T>;
}

/**
 * An interface to support pluggable http clients
 * In the future, we should have a TestingHttpClient and a FetchHttpClient (or whatever http client we go with)
 */
export interface IHttpClientImplementation {
  get<T = any>(config: IRequestBuilderConfig): PromiseLike<T>;
  post<T = any>(config: IRequestBuilderConfig): PromiseLike<T>;
  put<T = any>(config: IRequestBuilderConfig): PromiseLike<T>;
  patch<T = any>(config: IRequestBuilderConfig): PromiseLike<T>;
  delete<T = any>(config: IRequestBuilderConfig): PromiseLike<T>;
}

export class InvalidAPIResponse extends Error {
  public data: { message: string };

  constructor(message: string, public originalResult: any) {
    super(message);
    this.data = { message };
  }
}

/**
 * An HTTP client that uses the AngularJS $http service
 * This client also handles non-data responses from Gate which is used to indicate the user is not authenticated
 * TODO: Can the re-authentication logic be moved somewhere else?
 */
class AngularJSHttpClient implements IHttpClientImplementation {
  delete = <T = any>(requestConfig: IRequestBuilderConfig) => this.request<T>('DELETE', requestConfig);
  get = <T = any>(requestConfig: IRequestBuilderConfig) => this.request<T>('GET', requestConfig);
  post = <T = any>(requestConfig: IRequestBuilderConfig) => this.request<T>('POST', requestConfig);
  put = <T = any>(requestConfig: IRequestBuilderConfig) => this.request<T>('PUT', requestConfig);
  patch = <T = any>(requestConfig: IRequestBuilderConfig) => this.request<T>('PATCH', requestConfig);

  private request<T>(
    method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE',
    requestConfig: IRequestBuilderConfig,
  ): PromiseLike<T> {
    return $http<T>({ ...requestConfig, method }).then((response) => {
      const contentType = response.headers('content-type');

      if (contentType) {
        // e.g application/json, application/hal+json
        const isJson = contentType.match(/application\/(.+\+)?json/);
        // e.g. application/yaml, application/x-yaml; it's regex, let's not get too fancy
        const isYaml = contentType.match(/application\/(.+-)?yaml/);
        const isZeroLengthHtml = contentType.includes('text/html') && (response as any).data === '';
        const isZeroLengthText = contentType.includes('text/plain') && (response as any).data === '';
        if (!(isJson || isYaml || isZeroLengthHtml || isZeroLengthText)) {
          AuthenticationInitializer.reauthenticateUser();
          throw new InvalidAPIResponse(invalidContentMessage, response);
        }
      }

      return response.data;
    });
  }
}

function joinPaths(...paths: IPrimitive[]) {
  // coerce paths toString() in case somebody sends in a url object
  // according to https://github.com/spinnaker/deck/pull/6927
  return paths
    .filter((path) => !isNil(path) && path !== '')
    .map((path) => path.toString())
    .map((path) => path.replace(/^\/+/, '')) // strip leading slashes
    .map((path) => path.replace(/\/+$/, '')) // strip trailing slashes
    .join('/');
}

/** The base request builder implementation */
export class RequestBuilder implements IRequestBuilder {
  static defaultHttpClient: IHttpClientImplementation = new AngularJSHttpClient();

  public constructor(
    protected config: IRequestBuilderConfig = makeRequestBuilderConfig(),
    protected _httpClient?: IHttpClientImplementation,
    protected _baseUrl?: string,
  ) {}

  // Factory function to create a child builder of the appropriate type
  protected builder(newRequest: IRequestBuilderConfig): this {
    return new RequestBuilder(newRequest, this.httpClient, this._baseUrl) as this;
  }

  protected get httpClient(): IHttpClientImplementation {
    return this._httpClient ?? RequestBuilder.defaultHttpClient;
  }

  protected get baseUrl(): string {
    return joinPaths(this._baseUrl ?? SETTINGS.gateUrl);
  }

  path(...paths: IPrimitive[]) {
    const url = joinPaths(this.config.url, ...paths.map((path) => encodeURIComponent(path)));
    return this.builder({ ...this.config, url });
  }

  headers(headers: Headers) {
    return this.builder({ ...this.config, headers: { ...this.config.headers, ...headers } });
  }

  // queryParams argument for backwards compat
  get<T>(queryParams: object = {}) {
    // Merge with existing params
    const params = { ...this.config.params, ...queryParams };
    const url = joinPaths(this.baseUrl, this.config.url);
    return this.httpClient.get<T>({ ...this.config, url, params });
  }

  post<T>(postData?: any) {
    // Check this.config.data for backwards compat
    const data = postData ?? this.config.data;
    const url = joinPaths(this.baseUrl, this.config.url);
    return this.httpClient.post<T>({ ...this.config, url, data });
  }

  put<T>(putData?: any) {
    // Check this.config.data for backwards compat
    const data = putData ?? this.config.data;
    const url = joinPaths(this.baseUrl, this.config.url);
    return this.httpClient.put<T>({ ...this.config, url, data });
  }

  patch<T>(putData?: any) {
    // Check this.config.data for backwards compat
    const data = putData ?? this.config.data;
    const url = joinPaths(this.baseUrl, this.config.url);
    return this.httpClient.patch<T>({ ...this.config, url, data });
  }

  delete<T>(deleteData?: any) {
    const data = deleteData ?? this.config.data;
    const url = joinPaths(this.baseUrl, this.config.url);
    return this.httpClient.delete<T>({ ...this.config, url, data });
  }

  useCache(cache = true) {
    return this.builder({ ...this.config, cache: cache as boolean });
  }

  query(queryParams: IParams) {
    const params = { ...this.config.params, ...queryParams };
    return this.builder({ ...this.config, params });
  }
}

/**
 * This class extends RequestBuilder and re-implements the deprecated API for backwards compat
 * @deprecated
 */
export class DeprecatedRequestBuilder extends RequestBuilder implements IDeprecatedRequestBuilder {
  protected builder = (newRequest: IRequestBuilderConfig): this => {
    return new DeprecatedRequestBuilder(newRequest, this._httpClient, this._baseUrl) as this;
  };
  public config: IRequestBuilderConfig;

  ////////  deprecated apis
  get baseUrl() {
    return super.baseUrl;
  }
  getList = this.get.bind(this);
  one = this.path.bind(this);
  all = this.path.bind(this);
  remove = this.delete.bind(this).bind(this);
  data = (data: any) => this.builder({ ...this.config, data });
  withParams = this.query.bind(this);
  useCache = (cache: boolean | ICache = true) => this.builder({ ...this.config, cache: cache as boolean });
}

class DeprecatedRequestBuilderRoot extends DeprecatedRequestBuilder {
  // Do not encode paths for the root API.one() call
  one = (...paths: string[]) => {
    const url = joinPaths(this.config.url, ...paths);
    return this.builder({ ...this.config, url });
  };
  all = (...paths: string[]) => {
    const url = joinPaths(this.config.url, ...paths);
    return this.builder({ ...this.config, url });
  };
}

export const invalidContentMessage = 'API response was neither JSON nor zero-length html or text';

export function makeRequestBuilderConfig(pathPrefix?: string): IRequestBuilderConfig {
  return {
    url: joinPaths(pathPrefix),
    cache: false,
    data: undefined,
    params: {},
    timeout: (SETTINGS.pollSchedule || 30000) * 2 + 5000,
    headers: { 'X-RateLimit-App': 'deck' },
  };
}

/** @deprecated use REST('/path/to/gate/endpoint') */
export const API: IDeprecatedRequestBuilder = new DeprecatedRequestBuilderRoot(makeRequestBuilderConfig());

/**
 * A REST client used to access Gate endpoints
 * @param staticPathPrefix a static string, i.e., '/proxies/foo/endpoint' --
 *        avoid dynamic strings like `/entity/${id}`, use .path('entity', id) instead
 */
export const REST = (staticPathPrefix?: string): IRequestBuilder => {
  return new RequestBuilder(makeRequestBuilderConfig(staticPathPrefix));
};
