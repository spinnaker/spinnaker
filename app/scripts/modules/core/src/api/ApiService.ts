import { IPromise, IRequestConfig } from 'angular';
import { $q, $http } from 'ngimport';
import { AuthenticationInitializer } from '../authentication/AuthenticationInitializer';
import { SETTINGS } from 'core/config/settings';
import { ICache } from 'core/cache';
import { isNil } from 'lodash';

export interface IRequestBuilder {
  config?: IRequestConfig;
  one?: (...urls: string[]) => IRequestBuilder;
  all?: (...urls: string[]) => IRequestBuilder;
  useCache?: (useCache: boolean | ICache) => IRequestBuilder;
  withParams?: (data: any) => IRequestBuilder;
  data?: (data: any) => IRequestBuilder;
  get?: (data?: any) => IPromise<any>;
  getList?: (data?: any) => IPromise<any>;
  post?: (data: any) => IPromise<any>;
  remove?: (data: any) => IPromise<any>;
  put?: (data: any) => IPromise<any>;
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
    timeout: SETTINGS.pollSchedule * 2 + 5000,
    headers: {
      'X-RateLimit-App': 'deck',
    },
  };

  public static readonly invalidContentMessage = 'API response was neither JSON nor zero-length html or text';

  private static getData(result: any): IPromise<any> {
    return $q((resolve, reject) => {
      const contentType = result.headers('content-type');
      if (contentType) {
        const isJson = contentType.includes('application/json');
        const isZeroLengthHtml = contentType.includes('text/html') && result.data === '';
        const isZeroLengthText = contentType.includes('text/plain') && result.data === '';
        if (!(isJson || isZeroLengthHtml || isZeroLengthText)) {
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
  private static getFn(config: IRequestConfig): (data: any) => IPromise<any> {
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
  private static postFn(config: IRequestConfig): (data: any) => IPromise<any> {
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
  private static removeFn(config: IRequestConfig): (data: any) => IPromise<any> {
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
  private static putFn(config: IRequestConfig): (data: any) => IPromise<any> {
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
      .filter(i => !isNil(i))
      .forEach((url: string) => (config.url = `${config.url}/${url.toString().replace(/^\/+/, '')}`));

    return this.baseReturn(config);
  }

  public static one(...urls: string[]): any {
    return this.init(urls);
  }

  public static all(...urls: string[]): any {
    return this.init(urls);
  }

  public static get baseUrl(): string {
    return SETTINGS.gateUrl.replace(/\/+$/, '');
  }
}
