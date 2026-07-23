import type {
  IHttpInterceptor,
  IHttpPromiseCallbackArg,
  IHttpProvider,
  IHttpService,
  IQService,
  IRequestConfig,
} from 'angular';
import { module } from 'angular';

import {
  getIapSessionRequestState,
  hasIapSessionRefreshedSince,
  IAP_SESSION_REFRESH_URL,
  markIapSessionRefreshAttempted,
  refreshIapSession,
  shouldRefreshIapSession,
} from './iapSessionRefresh';

const IAP_RETRY_MARKER = '__iapSessionRetry';

interface IIapRetryConfig extends IRequestConfig {
  [IAP_RETRY_MARKER]?: true;
}

export class IapInterceptor implements IHttpInterceptor {
  public static $inject = ['$injector', '$q'];
  public constructor(private $injector: angular.auto.IInjectorService, private $q: IQService) {}

  public request = (config: IRequestConfig): IRequestConfig => {
    const retryConfig = config as IIapRetryConfig;
    const isRetry = retryConfig[IAP_RETRY_MARKER] === true;
    delete retryConfig[IAP_RETRY_MARKER];
    if (isRetry) {
      markIapSessionRefreshAttempted(config);
    } else {
      getIapSessionRequestState(config);
    }
    return config;
  };

  public responseError = <T>(response: IHttpPromiseCallbackArg<T>): PromiseLike<T> => {
    const requestConfig = response.config;
    const requestState = getIapSessionRequestState(requestConfig);
    if (shouldRefreshIapSession(response.status, requestConfig, requestState.refreshAttempted)) {
      const $http = this.$injector.get('$http') as IHttpService;
      const retry = () => {
        const retryConfig: IIapRetryConfig = { ...requestConfig, [IAP_RETRY_MARKER]: true };
        return $http(retryConfig).then((result: any) => result);
      };
      return hasIapSessionRefreshedSince(requestState.generation)
        ? retry()
        : refreshIapSession(() => $http.get(IAP_SESSION_REFRESH_URL)).then(retry, () => this.$q.reject(response));
    }

    return this.$q.reject(response);
  };
}

export const IAP_INTERCEPTOR = 'spinnaker.core.iap.interceptor';
module(IAP_INTERCEPTOR, [])
  .service('iapInterceptor', IapInterceptor)
  .config([
    '$httpProvider',
    ($httpProvider: IHttpProvider) => {
      $httpProvider.interceptors.push('iapInterceptor');
    },
  ]);
