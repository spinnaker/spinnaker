import {module} from 'angular';
import {
  AUTHENTICATION_INITIALIZER_SERVICE,
  AuthenticationInitializer
} from '../authentication/authentication.initializer.service';

interface DefaultParams {
  timeout: number;
}

export interface IRequestBuilder {
  config?: ng.IRequestConfig;
  one?: IRequestBuilder;
  all?: IRequestBuilder;
  useCache?: IRequestBuilder;
  withParams?: IRequestBuilder;
  data?: IRequestBuilder;
  get?: IRequestBuilder;
  getList?: IRequestBuilder;
  post?: IRequestBuilder;
  remove?: IRequestBuilder;
  put?: IRequestBuilder;
}

export class Api {

  static get $inject() {
    return ['$q', '$http', 'settings', 'authenticationInitializer'];
  }

  private gateUrl: string;
  private defaultParams: DefaultParams;

  constructor(private $q: ng.IQService,
              private $http: ng.IHttpService,
              settings: any,
              private authenticationIntializer: AuthenticationInitializer) {
    this.gateUrl = settings.gateUrl;
    this.defaultParams = {
      timeout: settings.pollSchedule * 2 + 5000
    };
  }

  private getData(result: any): ng.IPromise<any> {
    return this.$q((resolve: ng.IQResolveReject<any>, reject: ng.IQResolveReject<any>) => {
      const contentType = result.headers('content-type');
      if (contentType) {
        const isJson = contentType.includes('application/json');
        const isZeroLengthHtml = (contentType.includes('text/html') && (result.data === ''));
        if (!(isJson || isZeroLengthHtml)) {
          this.authenticationIntializer.reauthenticateUser();
          reject(result);
        }
      }

      return resolve(result.data);
    });
  }

  private internalOne(config: ng.IRequestConfig): IRequestBuilder {
    return (...urls: string[]) => {
      urls.forEach((url: string) => {
        if (url) {
          config.url = `${config.url}/${url}`;
        }
      });

      return this.baseReturn(config);
    };
  }

  private useCacheFn(config: ng.IRequestConfig): IRequestBuilder {
    return (useCache = true) => {
      config.cache = useCache;
      return this.baseReturn(config);
    };
  }

  private withParamsFn(config: ng.IRequestConfig): IRequestBuilder {
    return (params: any) => {
      if (params) {
        config.params = params;
      }

      return this.baseReturn(config);
    };
  }

  // sets the data for PUT and POST operations
  private dataFn(config: ng.IRequestConfig): IRequestBuilder {
    return (data: any) => {
      if (data) {
        config.data = data;
      }

      return this.baseReturn(config);
    };
  }

  // HTTP GET operation
  private getFn(config: ng.IRequestConfig): IRequestBuilder {
    return (params: any) => {
      config.method = 'get';
      Object.assign(config, this.defaultParams);
      if (params) {
        config.params = params;
      }

      return this.$http(config).then((result: any) => this.getData(result));
    };
  }

  // HTTP POST operation
  private postFn(config: ng.IRequestConfig): IRequestBuilder {
    return (data: any) => {
      config.method = 'post';
      if (data) {
        config.data = data;
      }
      Object.assign(config, this.defaultParams);

      return this.$http(config).then((result: any) => this.getData(result));
    };
  }

  // HTTP DELETE operation
  private removeFn(config: ng.IRequestConfig): IRequestBuilder {
    return (params: any) => {
      config.method = 'delete';
      if (params) {
        config.params = params;
      }
      Object.assign(config, this.defaultParams);

      return this.$http(config).then((result: any) => this.getData(result));
    };
  }

  // HTTP PUT operation
  private putFn(config: ng.IRequestConfig): IRequestBuilder {
    return (data: any) => {
      config.method = 'put';
      if (data) {
        config.data = data;
      }
      Object.assign(config, this.defaultParams);

      return this.$http(config).then((result: any) => this.getData(result));
    };
  }

  private baseReturn(config: ng.IRequestConfig): IRequestBuilder {
    return {
      config: config,
      one: this.internalOne(config),
      all: this.internalOne(config),
      useCache: this.useCacheFn(config),
      withParams: this.withParamsFn(config),
      data: this.dataFn(config),
      get: this.getFn(config),
      getList: this.getFn(config),
      post: this.postFn(config),
      remove: this.removeFn(config),
      put: this.putFn(config)
    };
  }

  private init(urls: string[]) {
    const config: ng.IRequestConfig = {
      method: '',
      url: this.gateUrl
    };
    urls.forEach((url: string) => config.url = `${config.url}/${url}`);

    return this.baseReturn(config);
  }

  public one(...urls: string[]): any {
    return this.init(urls);
  }

  public all(...urls: string[]): any {
    return this.init(urls);
  }

  public get baseUrl(): string {
    return this.gateUrl;
  }
}

export const API_SERVICE = 'spinnaker.core.api.provider';
module(API_SERVICE, [require('../config/settings'), AUTHENTICATION_INITIALIZER_SERVICE])
  .service('API', Api);
