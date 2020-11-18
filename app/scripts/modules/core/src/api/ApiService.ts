import { IRequestConfig } from 'angular';
import { $q, $http } from 'ngimport';
import { AuthenticationInitializer } from '../authentication/AuthenticationInitializer';
import { SETTINGS } from 'core/config/settings';
import { ICache } from 'core/cache';
import { isNil } from 'lodash';

type IPrimitive = string | boolean | number;
type IParams = Record<string, IPrimitive | IPrimitive[]>;

export interface IRequestBuilder {
  config?: IRequestConfig;
  one?: (...urls: string[]) => IRequestBuilder;
  all?: (...urls: string[]) => IRequestBuilder;
  useCache?: (useCache?: boolean | ICache) => IRequestBuilder;
  withParams?: (params: IParams) => IRequestBuilder;
  data?: (data: any) => IRequestBuilder;
  get?: <T = any>(params?: IParams) => PromiseLike<T>;
  getList?: <T = any>(params?: IParams) => PromiseLike<T>;
  post?: <T = any>(data?: any) => PromiseLike<T>;
  remove?: <T = any>(params?: IParams) => PromiseLike<T>;
  put?: <T = any>(data?: any) => PromiseLike<T>;
}

export class InvalidAPIResponse extends Error {
  public data: { message: string };
  constructor(message: string, public originalResult: any) {
    super(message);
    this.data = { message };
  }
}

export class API {
  private static defaultParams = {
    timeout: (SETTINGS.pollSchedule || 30000) * 2 + 5000,
    headers: {
      'X-RateLimit-App': 'deck',
    },
  };

  public static readonly invalidContentMessage = 'API response was neither JSON nor zero-length html or text';

  private static getData(result: any): PromiseLike<any> {
    return $q((resolve, reject) => {
      const contentType = result.headers('content-type');
      if (contentType) {
        const isJson = contentType.match(/application\/(.+\+)?json/); // e.g application/json, application/hal+json
        // e.g. application/yaml, application/x-yaml; it's regex, let's not get too fancy
        const isYaml = contentType.match(/application\/(.+-)?yaml/);
        const isZeroLengthHtml = contentType.includes('text/html') && result.data === '';
        const isZeroLengthText = contentType.includes('text/plain') && result.data === '';
        if (!(isJson || isYaml || isZeroLengthHtml || isZeroLengthText)) {
          AuthenticationInitializer.reauthenticateUser();
          reject(new InvalidAPIResponse(API.invalidContentMessage, result));
        }
      }

      return resolve(result.data);
    });
  }

  private static internalOne(config: IRequestConfig): (...urls: string[]) => IRequestBuilder {
    return (...urls: string[]) => {
      urls.forEach((url: string) => {
        if (url) {
          config.url = `${config.url}/${url}`;
        }
      });

      return this.baseReturn(config);
    };
  }

  private static useCacheFn(config: IRequestConfig): (useCache: boolean | ICache) => IRequestBuilder {
    return (useCache = true) => {
      config.cache = useCache;
      return this.baseReturn(config);
    };
  }

  private static withParamsFn(config: IRequestConfig): (params: any) => IRequestBuilder {
    return (params: any) => {
      if (params) {
        config.params = params;
      }

      return this.baseReturn(config);
    };
  }

  // sets the data for PUT and POST operations
  private static dataFn(config: IRequestConfig): (data: any) => IRequestBuilder {
    return (data: any) => {
      if (data) {
        config.data = data;
      }

      return this.baseReturn(config);
    };
  }

  // HTTP GET operation
  private static getFn(config: IRequestConfig): (params: any) => PromiseLike<any> {
    return (params: any) => {
      config.method = 'get';
      Object.assign(config, this.defaultParams);
      if (params) {
        config.params = params;
      }

      return $http(config).then((result: any) => this.getData(result));
    };
  }

  // HTTP POST operation
  private static postFn(config: IRequestConfig): (data: any) => PromiseLike<any> {
    return (data: any) => {
      config.method = 'post';
      if (data) {
        config.data = data;
      }
      Object.assign(config, this.defaultParams);

      return $http(config).then((result: any) => this.getData(result));
    };
  }

  // HTTP DELETE operation
  private static removeFn(config: IRequestConfig): (params: any) => PromiseLike<any> {
    return (params: any) => {
      config.method = 'delete';
      if (params) {
        config.params = params;
      }
      Object.assign(config, this.defaultParams);

      return $http(config).then((result: any) => this.getData(result));
    };
  }

  // HTTP PUT operation
  private static putFn(config: IRequestConfig): (data: any) => PromiseLike<any> {
    return (data: any) => {
      config.method = 'put';
      if (data) {
        config.data = data;
      }
      Object.assign(config, this.defaultParams);

      return $http(config).then((result: any) => this.getData(result));
    };
  }

  private static baseReturn(config: IRequestConfig): IRequestBuilder {
    return {
      config,
      one: this.internalOne(config),
      all: this.internalOne(config),
      useCache: this.useCacheFn(config),
      withParams: this.withParamsFn(config),
      data: this.dataFn(config),
      get: this.getFn(config),
      getList: this.getFn(config),
      post: this.postFn(config),
      remove: this.removeFn(config),
      put: this.putFn(config),
    };
  }

  private static init(urls: string[]) {
    const config: IRequestConfig = {
      method: '',
      url: this.baseUrl,
    };
    urls
      .filter((i) => !isNil(i))
      .forEach((url: string) => (config.url = `${config.url}/${url.toString().replace(/^\/+/, '')}`));

    return this.baseReturn(config);
  }

  public static one(...urls: string[]): IRequestBuilder {
    return this.init(urls);
  }

  public static all(...urls: string[]): IRequestBuilder {
    return this.init(urls);
  }

  public static get baseUrl(): string {
    return SETTINGS.gateUrl.replace(/\/+$/, '');
  }
}
